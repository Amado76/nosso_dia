package com.beehome.media.service;

import com.beehome.family.service.FamilyAuthorizationService;
import com.beehome.media.dto.*;
import com.beehome.media.entity.Media;
import com.beehome.media.exception.MediaException;
import com.beehome.media.repository.MediaRepository;
import com.beehome.media.storage.*;
import com.beehome.photorecord.repository.PhotoRecordMediaRepository;
import com.beehome.shared.exception.InputException;
import java.io.IOException;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaService {
    private static final Logger log = LoggerFactory.getLogger(MediaService.class);
    private final MediaRepository media;
    private final PhotoRecordMediaRepository links;
    private final FamilyAuthorizationService authorization;
    private final MediaStorage storage;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final long maxBytes;
    public MediaService(MediaRepository media, PhotoRecordMediaRepository links, FamilyAuthorizationService authorization,
            MediaStorage storage, TransactionTemplate transactions, Clock clock,
            @Value("${media.max-bytes:10485760}") long maxBytes) {
        this.media=media; this.links=links; this.authorization=authorization; this.storage=storage;
        this.transactions=transactions; this.clock=clock; this.maxBytes=maxBytes;
    }
    public MediaResponse upload(UUID user, UUID family, MultipartFile file) {
        authorization.requireMembership(user, family);
        if (file == null) throw new InputException();
        byte[] bytes;
        try { bytes=ImageValidation.read(file.getInputStream(), file.getSize(), maxBytes); }
        catch (MediaException e) { throw e; }
        catch (IOException e) { throw MediaException.failed(); }
        String mime=ImageValidation.mime(bytes);
        UUID id=UUID.randomUUID(); String key=family + "/" + id;
        String filename=file.getOriginalFilename();
        if (filename != null) filename=filename.replace('\\','/');
        if (filename != null) filename=filename.substring(filename.lastIndexOf('/')+1);
        if (filename != null && filename.length()>255) filename=filename.substring(0,255);
        try { storage.store(key, bytes); }
        catch (IOException e) { log.warn("Media storage failed for {}", id); throw MediaException.failed(); }
        try {
            String finalFilename=filename;
            return transactions.execute(status -> MediaResponse.from(media.saveAndFlush(
                    new Media(id, family, user, key, finalFilename, mime, bytes.length, clock.instant()))));
        } catch (RuntimeException e) {
            try { storage.delete(key); } catch (IOException cleanup) { log.warn("Media cleanup failed for {}", id); }
            throw e;
        }
    }
    @Transactional(readOnly=true)
    public MediaPage list(UUID user, UUID family, boolean unattached, int page, int size) {
        authorization.requireMembership(user, family);
        if (page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE-1) throw new InputException();
        var slice=media.list(family, unattached, PageRequest.of(page,size));
        return new MediaPage(slice.getContent().stream().map(MediaResponse::from).toList(),page,size,slice.hasNext());
    }
    @Transactional(readOnly=true)
    public Content content(UUID user, UUID family, UUID id) {
        authorization.requireMembership(user, family);
        var item=media.findByFamilyIdAndId(family,id).orElseThrow(MediaException::notFound);
        try { return new Content(storage.load(item.getStorageKey()), item.getMimeType()); }
        catch (IOException e) { log.warn("Media content unavailable for {}", id); throw MediaException.failed(); }
    }
    /** Serialize new image references with deletion; callers hold the surrounding transaction. */
    @Transactional
    public boolean lockImage(UUID user, UUID family, UUID id) {
        authorization.requireMembership(user,family);
        return media.lock(family,id).filter(item -> "IMAGE".equals(item.getType())).isPresent();
    }
    public record Content(Resource resource, String mimeType) {}
    @Transactional
    public void delete(UUID user, UUID family, UUID id) {
        authorization.requireMembership(user, family);
        var item=media.lock(family,id).orElseThrow(MediaException::notFound);
        if (links.existsByMediaId(id) || media.usedAsBookCover(id)) throw MediaException.inUse();
        try { storage.delete(item.getStorageKey()); }
        catch (IOException e) { log.warn("Media deletion failed for {}", id); throw MediaException.failed(); }
        media.delete(item);
        media.flush();
    }
}
