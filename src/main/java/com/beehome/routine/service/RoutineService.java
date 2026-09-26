package com.beehome.routine.service;

import com.beehome.routine.dto.*;
import com.beehome.routine.entity.*;
import com.beehome.routine.exception.RoutineException;
import com.beehome.routine.repository.*;
import com.beehome.family.service.FamilyAuthorizationService;
import com.beehome.family.exception.FamilyException;
import com.beehome.familymember.service.FamilyMemberService;
import com.beehome.shared.dto.ItemOrderRequest;
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
public class RoutineService {
    private final RoutineRepository routines;
    private final RoutineItemRepository items;
    private final RoutineItemTagRepository itemTags;
    private final TagService tags;
    private final FamilyAuthorizationService authorization;
    private final FamilyMemberService members;
    private final Clock clock;
    public RoutineService(RoutineRepository routines, RoutineItemRepository items, RoutineItemTagRepository itemTags, TagService tags,
            FamilyAuthorizationService authorization, FamilyMemberService members, Clock clock) {
        this.routines = routines; this.items = items; this.itemTags=itemTags; this.tags=tags;
        this.authorization = authorization; this.members = members; this.clock = clock;
    }
    @Transactional
    public RoutineResponse create(UUID user, UUID family, CreateRoutineRequest request) {
        authorization.requireEditor(authorization.requireMembership(user, family));
        var routine = routines.save(new Routine(family, request.name(), request.daysOfWeek(), request.startDate(), request.endDate(), clock.instant()));
        return RoutineResponse.from(routine, List.of());
    }
    @Transactional(readOnly = true)
    public RoutinePage list(UUID user, UUID family, String includeInactive, int page, int size) {
        authorization.requireMembership(user, family);
        if (!Set.of("true", "false").contains(includeInactive)) throw new InputException();
        if (page < 0 || size < 1 || size > 100 || (long) page * size >= Integer.MAX_VALUE) throw FamilyException.invalidPage();
        var slice = routines.list(family, Boolean.parseBoolean(includeInactive), PageRequest.of(page, size));
        return new RoutinePage(slice.stream().map(RoutinePage.Row::from).toList(), page, size, slice.hasNext());
    }
    @Transactional(readOnly = true)
    public RoutineResponse get(UUID user, UUID family, UUID routine) {
        authorization.requireMembership(user, family);
        return detail(routines.findByFamilyIdAndId(family, routine).orElseThrow(RoutineException::notFound));
    }
    @Transactional
    public RoutineResponse edit(UUID user, UUID family, UUID id, PatchRoutineRequest request) {
        var routine = editable(user, family, id);
        var fields = request.fields();
        if (fields.isEmpty()) throw new InputException();
        routine.edit(fields.contains("name") ? request.name() : routine.getName(),
                fields.contains("daysOfWeek") ? request.daysOfWeek() : routine.getDaysOfWeek(),
                fields.contains("startDate") ? request.startDate() : routine.getStartDate(),
                fields.contains("endDate") ? request.endDate() : routine.getEndDate(), clock.instant());
        return detail(routine);
    }
    @Transactional
    public RoutineResponse setActive(UUID user, UUID family, UUID id, boolean active) {
        var routine = editable(user, family, id);
        routine.setActive(active, clock.instant());
        return detail(routine);
    }
    @Transactional
    public RoutineItemResponse createItem(UUID user, UUID family, UUID id, CreateRoutineItemRequest request) {
        editable(user, family, id);
        tags.requireTags(user,family,request.tagIds());
        if (request.familyMemberId() == null) throw new InputException();
        members.requireActive(user, family, request.familyMemberId(), true);
        if (items.countByRoutineId(id) >= 500) throw new InputException();
        var item=items.saveAndFlush(new RoutineItem(id, family, request.familyMemberId(),
                request.title(), request.description(), request.scheduledTime(), request.sortOrder(), clock.instant()));
        replaceTags(user,family,item.getId(),request.tagIds());
        return itemResponses(family,List.of(item)).getFirst();
    }
    @Transactional
    public RoutineItemResponse editItem(UUID user, UUID family, UUID id, UUID itemId, PatchRoutineItemRequest request) {
        editable(user, family, id);
        var item = item(family, id, itemId);
        var fields = request.fields();
        if (fields.isEmpty()) throw new InputException();
        if(fields.contains("tagIds")) tags.requireTags(user,family,request.tagIds());
        UUID member = fields.contains("familyMemberId") ? request.familyMemberId() : item.getFamilyMemberId();
        if (member == null) throw new InputException();
        members.requireActive(user, family, member, true);
        var now = clock.instant();
        item.edit(fields.contains("title") ? request.title() : item.getTitle(),
                fields.contains("description") ? request.description() : item.getDescription(),
                fields.contains("scheduledTime") ? request.scheduledTime() : item.getScheduledTime(),
                fields.contains("sortOrder") ? request.sortOrder() : Integer.valueOf(item.getSortOrder()), now);
        item.assign(member, now);
        if(fields.contains("tagIds")) replaceTags(user,family,itemId,request.tagIds());
        return itemResponses(family,List.of(item)).getFirst();
    }
    @Transactional
    public RoutineItemResponse setItemActive(UUID user, UUID family, UUID id, UUID itemId, boolean active) {
        editable(user, family, id);
        var item = item(family, id, itemId);
        if (active) members.requireActive(user, family, item.getFamilyMemberId(), true);
        item.setActive(active, clock.instant());
        return itemResponses(family,List.of(item)).getFirst();
    }
    @Transactional
    public RoutineResponse reorder(UUID user, UUID family, UUID id, ItemOrderRequest request) {
        var routine = editable(user, family, id);
        var all = allItems(family, id);
        var expected = all.stream().map(RoutineItem::getId).collect(Collectors.toSet());
        if (request.items() != null && request.items().stream().anyMatch(entry -> entry != null && entry.id() != null && !expected.contains(entry.id()))) {
            throw RoutineException.itemNotFound();
        }
        var order = request.validate(expected);
        var now = clock.instant();
        all.forEach(item -> item.reorder(order.get(item.getId()), now));
        items.flush();
        return detail(routine);
    }
    private Routine editable(UUID user, UUID family, UUID id) {
        var role = authorization.requireMembership(user, family);
        var routine = routines.lock(family, id).orElseThrow(RoutineException::notFound);
        authorization.requireEditor(role);
        return routine;
    }
    private RoutineItem item(UUID family, UUID routine, UUID item) {
        return items.findByFamilyIdAndRoutineIdAndId(family, routine, item).orElseThrow(RoutineException::itemNotFound);
    }
    private List<RoutineItem> allItems(UUID family, UUID routine) {
        var result = items.findByFamilyIdAndRoutineIdOrderBySortOrderAscIdAsc(family, routine, PageRequest.of(0, 501));
        if (result.size() > 500) throw new InputException();
        return result;
    }
    private RoutineResponse detail(Routine routine) {
        return RoutineResponse.from(routine, itemResponses(routine.getFamilyId(),allItems(routine.getFamilyId(), routine.getId())));
    }
    private void replaceTags(UUID user,UUID family,UUID itemId,List<UUID> ids) {
        tags.requireTags(user,family,ids);
        itemTags.deleteByRoutineItemId(itemId);
        itemTags.flush();
        itemTags.saveAll(ids.stream().map(id -> new RoutineItemTag(family,itemId,id)).toList());
    }
    private List<RoutineItemResponse> itemResponses(UUID family,List<RoutineItem> rows) {
        if(rows.isEmpty()) return List.of();
        var links=itemTags.findByRoutineItemIdIn(rows.stream().map(RoutineItem::getId).toList());
        Map<UUID,TagSummary> summaries=tags.summaries(family,links.stream().map(RoutineItemTag::getTagId).collect(Collectors.toSet()));
        Map<UUID,List<TagSummary>> byItem=new HashMap<>();
        for(var link:links) byItem.computeIfAbsent(link.getRoutineItemId(),_ -> new ArrayList<>()).add(summaries.get(link.getTagId()));
        return rows.stream().map(row -> RoutineItemResponse.from(row,byItem.getOrDefault(row.getId(),List.of()).stream()
                .sorted(Comparator.comparing(TagSummary::name).thenComparing(TagSummary::id)).toList())).toList();
    }
}
