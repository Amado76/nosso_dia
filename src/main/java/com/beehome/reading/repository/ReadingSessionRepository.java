package com.beehome.reading.repository;
import com.beehome.reading.entity.*;
import java.util.*;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
public interface ReadingSessionRepository extends JpaRepository<ReadingSession,UUID> {
    Optional<ReadingSession> findByFamilyIdAndChildIdAndId(UUID familyId,UUID childId,UUID id);
    @Query("select s from ReadingSession s join ChildBook c on c.id=s.childBookId where s.familyId=:family and s.childId=:child and s.date between :from and :to and (:book is null or c.bookId=:book) order by s.date desc,s.createdAt desc,s.id desc")
    Slice<ReadingSession> history(UUID family,UUID child,LocalDate from,LocalDate to,UUID book,Pageable page);
    @Query("select distinct s.date from ReadingSession s where s.familyId=:family and s.childId=:child and s.date between :from and :to order by s.date")
    List<LocalDate> dates(UUID family,UUID child,LocalDate from,LocalDate to);
    interface DayCount { LocalDate getDate(); long getSessions(); }
    @Query("select s.date as date,count(s) as sessions from ReadingSession s where s.familyId=:family and s.childId=:child and s.date between :from and :to group by s.date order by s.date")
    List<DayCount> dailyCounts(UUID family,UUID child,LocalDate from,LocalDate to);
    interface Totals { long getSessions(); long getMinutes(); long getPages(); long getBooks(); }
    @Query("select count(s) as sessions,coalesce(sum(s.minutes),0) as minutes,coalesce(sum(s.pagesRead),0) as pages,count(distinct c.bookId) as books from ReadingSession s join ChildBook c on c.id=s.childBookId where s.familyId=:family and s.childId=:child and s.date between :from and :to")
    Totals totals(UUID family,UUID child,LocalDate from,LocalDate to);
    interface Position { UUID getChildBookId(); Integer getEndPage(); }
    @Query(value="select distinct on (child_book_id) child_book_id as childBookId,end_page as endPage from beehome.reading_sessions where child_book_id in (:ids) and end_page is not null order by child_book_id,date desc,created_at desc,id desc",nativeQuery=true)
    List<Position> positions(Collection<UUID> ids);
    @Query("select count(s) from ReadingSession s join ChildBook c on c.id=s.childBookId where c.bookId=:book and (s.endPage>:total or s.pagesRead>:total)")
    long exceeding(UUID book,int total);
}
