package com.beehome.reading.service;

import com.beehome.reading.dto.*;
import com.beehome.reading.entity.*;
import com.beehome.reading.exception.ReadingException;
import com.beehome.reading.repository.*;
import com.beehome.family.service.FamilyAuthorizationService;
import com.beehome.familymember.service.FamilyMemberService;
import com.beehome.familymember.entity.MemberType;
import com.beehome.familymember.exception.FamilyMemberException;
import com.beehome.media.service.MediaService;
import com.beehome.shared.exception.InputException;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadingService {
    private final BookRepository books;
    private final ChildBookRepository journeys;
    private final ReadingSessionRepository sessions;
    private final FamilyAuthorizationService authorization;
    private final FamilyMemberService members;
    private final MediaService media;
    private final Clock clock;
    private final int maxPeriodDays;
    public ReadingService(BookRepository books, ChildBookRepository journeys, ReadingSessionRepository sessions,
            FamilyAuthorizationService authorization, FamilyMemberService members, MediaService media, Clock clock, @Value("${app.reports.max-period-days:366}") int maxPeriodDays) {
        this.books=books; this.journeys=journeys; this.sessions=sessions; this.authorization=authorization;
        this.members=members; this.media=media; this.clock=clock;
        if(maxPeriodDays<1) throw new IllegalArgumentException("app.reports.max-period-days must be positive");
        this.maxPeriodDays=maxPeriodDays;
    }
    private void editor(UUID user,UUID family) { authorization.requireEditor(authorization.requireMembership(user,family)); }
    private void child(UUID user,UUID family,UUID child,boolean write) {
        if(write) editor(user,family);
        var member=write ? members.requireActive(user,family,child,true) : members.get(user,family,child);
        if(member.memberType()!=MemberType.CHILD) throw FamilyMemberException.notFound();
    }
    private Book book(UUID family,UUID id,boolean lock) {
        return (lock ? books.lock(family,id) : books.findByFamilyIdAndId(family,id)).orElseThrow(ReadingException::missingBook);
    }
    private ChildBook journey(UUID family,UUID child,UUID id) {
        return journeys.findByFamilyIdAndChildIdAndId(family,child,id).orElseThrow(ReadingException::missingJourney);
    }
    private static PageRequest page(int page,int size) {
        if(page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE-1) throw new InputException();
        return PageRequest.of(page,size);
    }
    private void cover(UUID user,UUID family,UUID id) {
        if(id!=null && !media.lockImage(user,family,id)) throw ReadingException.invalid("BOOK_MEDIA_INVALID");
    }
    @Transactional
    public BookResponse createBook(UUID user,UUID family,BookRequest body) {
        editor(user,family); cover(user,family,body.coverMediaId());
        return BookResponse.from(books.save(new Book(family,user,body.title(),body.author(),body.isbn(),
                body.totalPages(),body.coverMediaId(),clock.instant())));
    }
    @Transactional(readOnly=true)
    public BookResponse getBook(UUID user,UUID family,UUID id) {
        authorization.requireMembership(user,family); return BookResponse.from(book(family,id,false));
    }
    @Transactional(readOnly=true)
    public ReadingPage<BookResponse> listBooks(UUID user,UUID family,int page,int size) {
        authorization.requireMembership(user,family); var rows=books.list(family,page(page,size));
        return new ReadingPage<>(rows.stream().map(BookResponse::from).toList(),page,size,rows.hasNext());
    }
    @Transactional
    public BookResponse replaceBook(UUID user,UUID family,UUID id,BookRequest body) {
        editor(user,family); var book=book(family,id,true); cover(user,family,body.coverMediaId());
        if(body.totalPages()!=null && sessions.exceeding(id,body.totalPages())>0) throw ReadingException.invalid("INVALID_PAGE_RANGE");
        book.replace(body.title(),body.author(),body.isbn(),body.totalPages(),body.coverMediaId(),clock.instant());
        return BookResponse.from(book);
    }
    @Transactional
    public void deleteBook(UUID user,UUID family,UUID id) {
        editor(user,family); var book=book(family,id,true);
        if(journeys.existsByBookId(id)) throw ReadingException.inUse();
        books.delete(book);
    }
    private void active(ChildBook journey,ChildBookStatus status) {
        if((status==ChildBookStatus.PLANNED || status==ChildBookStatus.READING)
                && journeys.existsByChildIdAndBookIdAndStatusInAndIdNot(journey.getChildId(),journey.getBookId(),
                List.of(ChildBookStatus.PLANNED,ChildBookStatus.READING),journey.getId())) throw ReadingException.active();
    }
    @Transactional
    public ChildBookDetail createJourney(UUID user,UUID family,UUID child,ChildBookRequest body) {
        child(user,family,child,true); if(body.bookId()==null) throw new InputException();
        var book=book(family,body.bookId(),true);
        var journey=new ChildBook(family,child,book.getId(),body.status()==null && !body.fields().contains("status")
                ? ChildBookStatus.PLANNED : body.status(),body.startedOn(),body.completedOn(),clock.instant());
        active(journey,journey.getStatus()); journeys.saveAndFlush(journey); return detail(journey,book,null);
    }
    @Transactional
    public ChildBookDetail patchJourney(UUID user,UUID family,UUID child,UUID id,ChildBookRequest body) {
        child(user,family,child,true); var journey=journey(family,child,id); var book=book(family,journey.getBookId(),true);
        if(body.fields().isEmpty() || body.fields().contains("bookId")) throw new InputException();
        var status=body.fields().contains("status") ? body.status() : journey.getStatus();
        active(journey,status);
        journey.change(status,
                body.fields().contains("startedOn") ? body.startedOn() : journey.getStartedOn(),
                body.fields().contains("completedOn") ? body.completedOn() : journey.getCompletedOn(),clock.instant());
        journeys.flush(); return details(List.of(journey),family).getFirst();
    }
    @Transactional(readOnly=true)
    public ChildBookDetail getJourney(UUID user,UUID family,UUID child,UUID id) {
        child(user,family,child,false); return details(List.of(journey(family,child,id)),family).getFirst();
    }
    @Transactional(readOnly=true)
    public ReadingPage<ChildBookDetail> listJourneys(UUID user,UUID family,UUID child,ChildBookStatus status,int page,int size) {
        child(user,family,child,false); var rows=journeys.list(family,child,status,page(page,size));
        return new ReadingPage<>(details(rows.getContent(),family),page,size,rows.hasNext());
    }
    private List<ChildBookDetail> details(List<ChildBook> rows,UUID family) {
        if(rows.isEmpty()) return List.of();
        var catalog=books.findByFamilyIdAndIdIn(family,rows.stream().map(ChildBook::getBookId).distinct().toList())
                .stream().collect(Collectors.toMap(Book::getId,b->b));
        var positions=sessions.positions(rows.stream().map(ChildBook::getId).toList()).stream()
                .collect(Collectors.toMap(ReadingSessionRepository.Position::getChildBookId,ReadingSessionRepository.Position::getEndPage));
        return rows.stream().map(c->detail(c,catalog.get(c.getBookId()),positions.get(c.getId()))).toList();
    }
    private ChildBookDetail detail(ChildBook c,Book b,Integer position) {
        Double percentage=position==null || b.getTotalPages()==null ? null : Math.round(10000.0*position/b.getTotalPages())/100.0;
        return new ChildBookDetail(c.getId(),c.getFamilyId(),c.getChildId(),c.getBookId(),BookResponse.from(b),c.getStatus(),
                c.getStartedOn(),c.getCompletedOn(),position,percentage,c.getCreatedAt(),c.getUpdatedAt());
    }

    private ReadingSession session(UUID family,UUID child,UUID id) {
        return sessions.findByFamilyIdAndChildIdAndId(family,child,id).orElseThrow(ReadingException::missingSession);
    }
    @Transactional
    public ReadingSessionDetail createSession(UUID user,UUID family,UUID child,ReadingSessionRequest body) {
        child(user,family,child,true); if(body.childBookId()==null) throw new InputException();
        var journey=journey(family,child,body.childBookId()); var book=book(family,journey.getBookId(),true);
        var session=new ReadingSession(family,child,journey.getId(),user,body.date(),body.minutes(),body.pagesRead(),
                body.startPage(),body.endPage(),body.notes(),book.getTotalPages(),clock.instant());
        sessions.save(session); return sessionDetail(session,book);
    }
    @Transactional(readOnly=true)
    public ReadingSessionDetail getSession(UUID user,UUID family,UUID child,UUID id) {
        child(user,family,child,false); var session=session(family,child,id);
        return sessionDetail(session,book(family,journey(family,child,session.getChildBookId()).getBookId(),false));
    }
    @Transactional
    public ReadingSessionDetail replaceSession(UUID user,UUID family,UUID child,UUID id,ReadingSessionRequest body) {
        child(user,family,child,true); var session=session(family,child,id);
        if(body.fields().contains("childBookId")) throw new InputException();
        var book=book(family,journey(family,child,session.getChildBookId()).getBookId(),true);
        session.replace(body.date(),body.minutes(),body.pagesRead(),body.startPage(),body.endPage(),body.notes(),book.getTotalPages(),clock.instant());
        return sessionDetail(session,book);
    }
    @Transactional
    public void deleteSession(UUID user,UUID family,UUID child,UUID id) {
        child(user,family,child,true); sessions.delete(session(family,child,id));
    }
    @Transactional(readOnly=true)
    public ReadingPage<ReadingSessionDetail> listSessions(UUID user,UUID family,UUID child,LocalDate from,LocalDate to,UUID book,int page,int size) {
        child(user,family,child,false);
        if(from!=null && to!=null && from.isAfter(to)) throw new InputException();
        if(book!=null) book(family,book,false);
        var rows=sessions.history(family,child,from==null ? LocalDate.of(1,1,1) : from,to==null ? LocalDate.of(9999,12,31) : to,book,page(page,size));
        return new ReadingPage<>(sessionDetails(rows.getContent(),family),page,size,rows.hasNext());
    }
    private List<ReadingSessionDetail> sessionDetails(List<ReadingSession> rows,UUID family) {
        if(rows.isEmpty()) return List.of();
        var links=journeys.findAllById(rows.stream().map(ReadingSession::getChildBookId).distinct().toList()).stream()
                .collect(Collectors.toMap(ChildBook::getId,ChildBook::getBookId));
        var catalog=books.findByFamilyIdAndIdIn(family,links.values()).stream().collect(Collectors.toMap(Book::getId,b->b));
        return rows.stream().map(s->sessionDetail(s,catalog.get(links.get(s.getChildBookId())))).toList();
    }
    private ReadingSessionDetail sessionDetail(ReadingSession s,Book b) {
        return new ReadingSessionDetail(s.getId(),s.getFamilyId(),s.getChildId(),s.getChildBookId(),BookResponse.from(b),s.getDate(),
                s.getMinutes(),s.getPagesRead(),s.getStartPage(),s.getEndPage(),s.getNotes(),s.getCreatedBy(),s.getCreatedAt(),s.getUpdatedAt());
    }
    private static void range(LocalDate from,LocalDate to,int limit) {
        if(from==null || to==null || from.isAfter(to) || java.time.temporal.ChronoUnit.DAYS.between(from,to)>=limit)
            throw new InputException();
    }
    @Transactional(readOnly=true)
    public ReadingSummary summary(UUID user,UUID family,UUID child,LocalDate from,LocalDate to) {
        child(user,family,child,false); range(from,to,maxPeriodDays);
        var totals=sessions.totals(family,child,from,to);
        return new ReadingSummary(from,to,totals.getSessions(),totals.getMinutes(),totals.getPages(),totals.getBooks(),journeys.completed(family,child,from,to));
    }
    public record ReadingDay(LocalDate date,long sessions) {}
    @Transactional(readOnly=true)
    public List<ReadingDay> historyCounts(UUID user,UUID family,UUID child,LocalDate from,LocalDate to) {
        child(user,family,child,false); range(from,to,maxPeriodDays);
        return sessions.dailyCounts(family,child,from,to).stream()
                .map(day -> new ReadingDay(day.getDate(),day.getSessions())).toList();
    }
    /** Calendar source: one distinct-date query, without loading session records. */
    @Transactional(readOnly=true)
    public List<LocalDate> historyDates(UUID user,UUID family,UUID child,LocalDate from,LocalDate to) {
        child(user,family,child,false); range(from,to,Math.max(31,maxPeriodDays)); return sessions.dates(family,child,from,to);
    }
}
