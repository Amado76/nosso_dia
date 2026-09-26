package com.beehome.photorecord.service;

import com.beehome.familymember.entity.MemberType;
import com.beehome.familymember.service.FamilyMemberService;
import com.beehome.media.repository.MediaRepository;
import com.beehome.photorecord.dto.*;
import com.beehome.photorecord.entity.*;
import com.beehome.photorecord.exception.PhotoRecordException;
import com.beehome.photorecord.repository.*;
import com.beehome.shared.exception.InputException;
import com.beehome.tag.dto.TagSummary;
import com.beehome.tag.service.TagService;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhotoRecordService {
    private final PhotoRecordRepository records;
    private final PhotoRecordMediaRepository links;
    private final PhotoRecordTagRepository recordTags;
    private final MediaRepository media;
    private final FamilyMemberService members;
    private final TagService tags;
    private final Clock clock;
    public PhotoRecordService(PhotoRecordRepository records, PhotoRecordMediaRepository links, PhotoRecordTagRepository recordTags,
            MediaRepository media, FamilyMemberService members, TagService tags, Clock clock) {
        this.records=records; this.links=links; this.recordTags=recordTags; this.media=media; this.members=members; this.tags=tags; this.clock=clock;
    }
    private void child(UUID user, UUID family, UUID child, boolean write) {
        var member=write ? members.requireActive(user,family,child,true) : members.get(user,family,child);
        if (member.memberType()!=MemberType.CHILD) throw PhotoRecordException.notFound();
    }
    private void lockMedia(UUID family, Collection<UUID> ids) {
        ids.stream().distinct().sorted().forEach(id -> {
            var item=media.lock(family,id).orElseThrow(PhotoRecordException::invalidMedia);
            if (!"IMAGE".equals(item.getType())) throw PhotoRecordException.invalidMedia();
        });
    }
    private PhotoRecordResponse response(PhotoRecord record, List<PhotoRecordMedia> entries, List<TagSummary> summaries) {
        return PhotoRecordResponse.from(record, summaries, entries.stream()
                .map(link -> new PhotoRecordResponse.MediaEntry(link.getMediaId(),"IMAGE",link.getPosition())).toList());
    }
    private List<TagSummary> summaries(UUID family, List<UUID> ids) {
        var found=tags.summaries(family,ids);
        return ids.stream().map(found::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(TagSummary::name).thenComparing(TagSummary::id)).toList();
    }
    private List<UUID> tagIds(UUID id) {
        return recordTags.findByPhotoRecordIdIn(List.of(id)).stream().map(PhotoRecordTag::getTagId).toList();
    }
    private PhotoRecordResponse response(PhotoRecord record) {
        return response(record,links.findByPhotoRecordIdOrderByPosition(record.getId()),summaries(record.getFamilyId(),tagIds(record.getId())));
    }
    private void setTags(PhotoRecord record, List<UUID> ids) {
        recordTags.deleteForRecord(record.getId());
        recordTags.flush();
        ids.forEach(id -> recordTags.save(new PhotoRecordTag(record.getFamilyId(),record.getId(),id)));
    }
    private void addLinks(PhotoRecord record, List<UUID> ids) {
        for (int i=0;i<ids.size();i++) links.save(new PhotoRecordMedia(record.getId(),record.getFamilyId(),ids.get(i),i));
    }
    @Transactional
    public PhotoRecordResponse create(UUID user, UUID family, UUID child, PhotoRecordRequest body) {
        child(user,family,child,true); if (body==null) throw new InputException();
        body.validate(); tags.requireTags(user,family,body.normalizedTagIds()); lockMedia(family,body.mediaIds());
        var record=records.save(new PhotoRecord(family,child,user,body.date(),body.normalizedDescription(),clock.instant()));
        addLinks(record,body.mediaIds());
        setTags(record,body.normalizedTagIds());
        return response(record);
    }
    @Transactional(readOnly=true)
    public PhotoRecordResponse get(UUID user, UUID family, UUID child, UUID id) {
        child(user,family,child,false);
        var record=records.findByFamilyIdAndChildIdAndId(family,child,id).orElseThrow(PhotoRecordException::notFound);
        return response(record);
    }
    @Transactional(readOnly=true)
    public PhotoRecordPage list(UUID user, UUID family, UUID child, LocalDate date, LocalDate from, LocalDate to,
            String query, List<UUID> tagIds, int page, int size) {
        child(user,family,child,false);
        if ((date!=null && (from!=null || to!=null)) || (from!=null && to!=null && from.isAfter(to))) throw new InputException();
        if (page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE-1) throw new InputException();
        String normalized=query==null || query.isBlank() ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (normalized.length()>120) throw new InputException();
        List<UUID> requested=tagIds==null ? List.of() : tagIds;
        tags.requireTags(user,family,requested);
        var slice=records.history(family,child,date!=null ? date : from==null ? LocalDate.of(1,1,1) : from,
                date!=null ? date : to==null ? LocalDate.of(9999,12,31) : to,
                normalized,requested.isEmpty() ? List.of(new UUID(0,0)) : requested,requested.size(),PageRequest.of(page,size));
        var ids=slice.getContent().stream().map(PhotoRecord::getId).toList();
        var grouped=ids.isEmpty() ? Map.<UUID,List<PhotoRecordMedia>>of() : links.findForRecords(ids).stream()
                .collect(Collectors.groupingBy(PhotoRecordMedia::getPhotoRecordId));
        var tagLinks=ids.isEmpty() ? Map.<UUID,List<UUID>>of() : recordTags.findByPhotoRecordIdIn(ids).stream()
                .collect(Collectors.groupingBy(PhotoRecordTag::getPhotoRecordId,
                        Collectors.mapping(PhotoRecordTag::getTagId,Collectors.toList())));
        var allTagIds=tagLinks.values().stream().flatMap(Collection::stream).distinct().toList();
        var tagSummaries=tags.summaries(family,allTagIds);
        return new PhotoRecordPage(slice.getContent().stream()
                .map(record -> response(record,grouped.getOrDefault(record.getId(),List.of()),
                        tagLinks.getOrDefault(record.getId(),List.of()).stream().map(tagSummaries::get)
                                .filter(Objects::nonNull).sorted(Comparator.comparing(TagSummary::name).thenComparing(TagSummary::id)).toList()))
                .toList(),page,size,slice.hasNext());
    }
    @Transactional(readOnly=true)
    public List<LocalDate> historyDates(UUID user, UUID family, UUID child, LocalDate from, LocalDate to) {
        child(user,family,child,false);
        return records.dates(family,child,from,to);
    }
    @Transactional(readOnly=true)
    public List<PhotoDay> historyCounts(UUID user, UUID family, UUID child, LocalDate from, LocalDate to) {
        child(user,family,child,false);
        return records.dayCounts(family,child,from,to).stream().map(row ->
                new PhotoDay(row.getDate(),row.getRecords(),row.getImages())).toList();
    }
    public record PhotoDay(LocalDate date,long records,long images) {}
    @Transactional(readOnly=true)
    public List<PhotoRecordResponse> historyRange(UUID user, UUID family, UUID child, LocalDate from, LocalDate to) {
        child(user,family,child,false);
        var rows=records.range(family,child,from,to);
        if (rows.isEmpty()) return List.of();
        var grouped=links.findForRecords(rows.stream().map(PhotoRecord::getId).toList()).stream()
                .collect(Collectors.groupingBy(PhotoRecordMedia::getPhotoRecordId));
        var tagLinks=recordTags.findByPhotoRecordIdIn(rows.stream().map(PhotoRecord::getId).toList()).stream()
                .collect(Collectors.groupingBy(PhotoRecordTag::getPhotoRecordId,
                        Collectors.mapping(PhotoRecordTag::getTagId,Collectors.toList())));
        var tagSummaries=tags.summaries(family,tagLinks.values().stream().flatMap(Collection::stream).distinct().toList());
        return rows.stream().map(record -> response(record,grouped.getOrDefault(record.getId(),List.of()),
                tagLinks.getOrDefault(record.getId(),List.of()).stream().map(tagSummaries::get)
                        .filter(Objects::nonNull).sorted(Comparator.comparing(TagSummary::name).thenComparing(TagSummary::id)).toList())).toList();
    }
    @Transactional
    public PhotoRecordResponse replace(UUID user, UUID family, UUID child, UUID id, PhotoRecordRequest body) {
        child(user,family,child,true); if (body==null) throw new InputException();
        body.validate(); tags.requireTags(user,family,body.normalizedTagIds());
        var record=records.lock(family,child,id).orElseThrow(PhotoRecordException::notFound);
        var old=links.findByPhotoRecordIdOrderByPosition(id);
        boolean sameMedia=old.stream().map(PhotoRecordMedia::getMediaId).toList().equals(body.mediaIds());
        boolean sameTags=Set.copyOf(tagIds(id)).equals(Set.copyOf(body.normalizedTagIds()));
        if (Objects.equals(record.getDate(),body.date())
                && Objects.equals(record.getDescription(),body.normalizedDescription()) && sameMedia && sameTags)
            return response(record);
        var ids=new ArrayList<>(body.mediaIds()); old.forEach(link -> ids.add(link.getMediaId()));
        lockMedia(family,ids);
        links.deleteForRecord(id);
        record.replace(body.date(),body.normalizedDescription(),clock.instant());
        addLinks(record,body.mediaIds());
        setTags(record,body.normalizedTagIds());
        links.flush();
        return response(record);
    }
    @Transactional
    public PhotoRecordResponse patch(UUID user, UUID family, UUID child, UUID id, PhotoRecordPatch body) {
        child(user,family,child,true);
        if (body==null) throw new InputException();
        if (body.tagIds()!=null) tags.requireTags(user,family,body.tagIds());
        var record=records.lock(family,child,id).orElseThrow(PhotoRecordException::notFound);
        LocalDate date=body.fields().containsKey("date") ? body.date() : record.getDate();
        String description=body.fields().containsKey("description") ? body.description() : record.getDescription();
        boolean tagsChanged=body.tagIds()!=null && !Set.copyOf(tagIds(id)).equals(Set.copyOf(body.tagIds()));
        if (!Objects.equals(date,record.getDate()) || !Objects.equals(description,record.getDescription()) || tagsChanged)
            record.replace(date,description,clock.instant());
        if (tagsChanged) setTags(record,body.tagIds());
        return response(record);
    }
    private PhotoRecordResponse changeMedia(UUID user, UUID family, UUID child, UUID id,
            java.util.function.Function<List<UUID>,List<UUID>> change) {
        child(user,family,child,true);
        var record=records.lock(family,child,id).orElseThrow(PhotoRecordException::notFound);
        var old=links.findByPhotoRecordIdOrderByPosition(id).stream().map(PhotoRecordMedia::getMediaId).toList();
        var next=change.apply(old);
        if (next==null || next.isEmpty() || next.size()>20 || next.contains(null) || new HashSet<>(next).size()!=next.size())
            throw new InputException();
        if (next.equals(old)) return response(record);
        var locked=new ArrayList<>(old); locked.addAll(next); lockMedia(family,locked);
        links.deleteForRecord(id); links.flush();
        addLinks(record,next); links.flush();
        record.replace(record.getDate(),record.getDescription(),clock.instant());
        return response(record);
    }
    @Transactional
    public PhotoRecordResponse setMedia(UUID user, UUID family, UUID child, UUID id, List<UUID> ids) {
        return changeMedia(user,family,child,id,old -> ids);
    }
    @Transactional
    public PhotoRecordResponse addMedia(UUID user, UUID family, UUID child, UUID id, PhotoRecordMediaChange body) {
        if (body==null) throw new InputException(); body.validate();
        return changeMedia(user,family,child,id,old -> {
            int position=body.position()==null ? old.size() : body.position();
            if (position<0 || position>old.size()) throw new InputException();
            var next=new ArrayList<>(old); next.add(position,body.mediaId()); return next;
        });
    }
    @Transactional
    public PhotoRecordResponse replaceMedia(UUID user, UUID family, UUID child, UUID id, UUID existing, PhotoRecordMediaReplacement body) {
        if (body==null) throw new InputException();
        return changeMedia(user,family,child,id,old -> {
            var next=new ArrayList<>(old); int index=next.indexOf(existing);
            if (index<0) throw PhotoRecordException.invalidMedia();
            next.set(index,body.mediaId()); return next;
        });
    }
    @Transactional
    public PhotoRecordResponse removeMedia(UUID user, UUID family, UUID child, UUID id, UUID mediaId) {
        return changeMedia(user,family,child,id,old -> {
            var next=new ArrayList<>(old);
            if (!next.remove(mediaId)) throw PhotoRecordException.invalidMedia();
            return next;
        });
    }
    @Transactional
    public void delete(UUID user, UUID family, UUID child, UUID id) {
        child(user,family,child,true);
        var record=records.lock(family,child,id).orElseThrow(PhotoRecordException::notFound);
        lockMedia(family,links.findByPhotoRecordIdOrderByPosition(id).stream().map(PhotoRecordMedia::getMediaId).toList());
        recordTags.deleteForRecord(id); links.deleteForRecord(id); records.delete(record);
    }
}
