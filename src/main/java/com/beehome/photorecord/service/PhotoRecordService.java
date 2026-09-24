package com.beehome.photorecord.service;

import com.beehome.familymember.entity.MemberType;
import com.beehome.familymember.service.FamilyMemberService;
import com.beehome.media.exception.MediaException;
import com.beehome.media.repository.MediaRepository;
import com.beehome.photorecord.dto.*;
import com.beehome.photorecord.entity.*;
import com.beehome.photorecord.exception.PhotoRecordException;
import com.beehome.photorecord.repository.*;
import com.beehome.shared.exception.InputException;
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
    private final MediaRepository media;
    private final FamilyMemberService members;
    private final Clock clock;
    public PhotoRecordService(PhotoRecordRepository records, PhotoRecordMediaRepository links, MediaRepository media,
            FamilyMemberService members, Clock clock) {
        this.records=records; this.links=links; this.media=media; this.members=members; this.clock=clock;
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
    private PhotoRecordResponse response(PhotoRecord record, List<PhotoRecordMedia> entries) {
        return PhotoRecordResponse.from(record, entries.stream()
                .map(link -> new PhotoRecordResponse.MediaEntry(link.getMediaId(),"IMAGE",link.getPosition())).toList());
    }
    private void addLinks(PhotoRecord record, List<UUID> ids) {
        for (int i=0;i<ids.size();i++) links.save(new PhotoRecordMedia(record.getId(),record.getFamilyId(),ids.get(i),i));
    }
    @Transactional
    public PhotoRecordResponse create(UUID user, UUID family, UUID child, PhotoRecordRequest body) {
        child(user,family,child,true); if (body==null) throw new InputException();
        body.validate(); lockMedia(family,body.mediaIds());
        var record=records.save(new PhotoRecord(family,child,user,body.date(),body.normalizedDescription(),clock.instant()));
        addLinks(record,body.mediaIds());
        return response(record,links.findByPhotoRecordIdOrderByPosition(record.getId()));
    }
    @Transactional(readOnly=true)
    public PhotoRecordResponse get(UUID user, UUID family, UUID child, UUID id) {
        child(user,family,child,false);
        var record=records.findByFamilyIdAndChildIdAndId(family,child,id).orElseThrow(PhotoRecordException::notFound);
        return response(record,links.findByPhotoRecordIdOrderByPosition(id));
    }
    @Transactional(readOnly=true)
    public PhotoRecordPage list(UUID user, UUID family, UUID child, LocalDate from, LocalDate to, int page, int size) {
        child(user,family,child,false);
        if (from!=null && to!=null && from.isAfter(to)) throw new InputException();
        if (page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE-1) throw new InputException();
        var slice=records.history(family,child,from==null ? LocalDate.of(1,1,1) : from,
                to==null ? LocalDate.of(9999,12,31) : to,PageRequest.of(page,size));
        var ids=slice.getContent().stream().map(PhotoRecord::getId).toList();
        var grouped=ids.isEmpty() ? Map.<UUID,List<PhotoRecordMedia>>of() : links.findForRecords(ids).stream()
                .collect(Collectors.groupingBy(PhotoRecordMedia::getPhotoRecordId));
        return new PhotoRecordPage(slice.getContent().stream()
                .map(record -> response(record,grouped.getOrDefault(record.getId(),List.of()))).toList(),page,size,slice.hasNext());
    }
    @Transactional
    public PhotoRecordResponse replace(UUID user, UUID family, UUID child, UUID id, PhotoRecordRequest body) {
        child(user,family,child,true); if (body==null) throw new InputException();
        body.validate();
        var record=records.lock(family,child,id).orElseThrow(PhotoRecordException::notFound);
        var old=links.findByPhotoRecordIdOrderByPosition(id);
        var ids=new ArrayList<>(body.mediaIds()); old.forEach(link -> ids.add(link.getMediaId()));
        lockMedia(family,ids);
        links.deleteForRecord(id);
        record.replace(body.date(),body.normalizedDescription(),clock.instant());
        addLinks(record,body.mediaIds());
        links.flush();
        return response(record,links.findByPhotoRecordIdOrderByPosition(id));
    }
    @Transactional
    public void delete(UUID user, UUID family, UUID child, UUID id) {
        child(user,family,child,true);
        var record=records.lock(family,child,id).orElseThrow(PhotoRecordException::notFound);
        lockMedia(family,links.findByPhotoRecordIdOrderByPosition(id).stream().map(PhotoRecordMedia::getMediaId).toList());
        links.deleteForRecord(id); records.delete(record);
    }
}
