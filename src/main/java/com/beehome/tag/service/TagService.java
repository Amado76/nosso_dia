package com.beehome.tag.service;

import com.beehome.family.service.FamilyAuthorizationService;
import com.beehome.shared.exception.InputException;
import com.beehome.tag.dto.*;
import com.beehome.tag.entity.Tag;
import com.beehome.tag.exception.TagException;
import com.beehome.tag.repository.TagRepository;
import java.text.Normalizer;
import java.time.Clock;
import java.util.*;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagService {
    private final TagRepository tags;
    private final FamilyAuthorizationService authorization;
    private final Clock clock;
    public TagService(TagRepository tags, FamilyAuthorizationService authorization, Clock clock) {
        this.tags = tags; this.authorization = authorization; this.clock = clock;
    }
    private void editor(UUID user, UUID family) { authorization.requireEditor(authorization.requireMembership(user, family)); }
    private Tag tag(UUID family, UUID id) { return tags.findByFamilyIdAndId(family, id).orElseThrow(TagException::notFound); }
    private void unique(Tag tag) {
        if (tags.existsByFamilyIdAndNormalizedNameAndIdNot(tag.getFamilyId(), tag.getNormalizedName(), tag.getId())) throw TagException.duplicate();
    }
    @Transactional
    public TagResponse create(UUID user, UUID family, TagRequest body) {
        editor(user, family);
        Tag tag = new Tag(family, body.name(), body.color(), clock.instant());
        unique(tag);
        try { return TagResponse.from(tags.saveAndFlush(tag)); }
        catch (DataIntegrityViolationException e) { throw translateConstraint(e); }
    }
    @Transactional(readOnly = true)
    public TagResponse get(UUID user, UUID family, UUID id) {
        authorization.requireMembership(user, family); return TagResponse.from(tag(family, id));
    }
    @Transactional(readOnly = true)
    public TagPage list(UUID user, UUID family, String query, int page, int size) {
        authorization.requireMembership(user, family);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE - 1) throw new InputException();
        String normalized = query == null ? null : Normalizer.normalize(query.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFC);
        if (normalized != null && normalized.length() > 120) throw new InputException();
        var slice = tags.list(family, normalized, PageRequest.of(page, size));
        return new TagPage(slice.stream().map(TagResponse::from).toList(), page, size, slice.hasNext());
    }
    @Transactional
    public TagResponse patch(UUID user, UUID family, UUID id, TagRequest body) {
        editor(user, family);
        if (body.fields().isEmpty()) throw new InputException();
        Tag tag = tag(family, id);
        tag.edit(body.fields().contains("name") ? body.name() : tag.getName(),
                body.fields().contains("color") ? body.color() : tag.getColor(), clock.instant());
        unique(tag);
        try { tags.flush(); return TagResponse.from(tag); }
        catch (DataIntegrityViolationException e) { throw translateConstraint(e); }
    }
    @Transactional
    public void delete(UUID user, UUID family, UUID id) {
        editor(user, family); tags.delete(tag(family, id));
    }

    @Transactional(readOnly = true)
    public List<Tag> requireTags(UUID user, UUID family, List<UUID> ids) {
        authorization.requireMembership(user, family);
        if (ids == null || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) throw new InputException();
        List<Tag> found = tags.findByFamilyIdAndIdIn(family, ids);
        if (found.size() != ids.size()) throw TagException.notFound();
        return found;
    }
    @Transactional(readOnly = true)
    public Map<UUID, TagSummary> summaries(UUID family, Collection<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, TagSummary> result = new HashMap<>();
        for (Tag tag : tags.findByFamilyIdAndIdIn(family, ids)) result.put(tag.getId(), TagSummary.from(tag));
        return result;
    }
    private static RuntimeException translateConstraint(DataIntegrityViolationException error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraint
                    && "tags_family_name_unique".equals(constraint.getConstraintName())) return TagException.duplicate();
        }
        return error;
    }
}
