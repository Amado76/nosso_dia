package com.beehome.dailyplan.service;

import com.beehome.dailyplan.dto.*;
import com.beehome.dailyplan.entity.*;
import com.beehome.dailyplan.exception.DailyPlanException;
import com.beehome.dailyplan.repository.*;
import com.beehome.family.service.FamilyAuthorizationService;
import com.beehome.family.service.FamilyService;
import com.beehome.familymember.service.FamilyMemberService;
import com.beehome.familymember.dto.FamilyMemberResponse;
import com.beehome.routine.repository.RoutineItemRepository;
import com.beehome.shared.dto.*;
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
public class DailyPlanService {
    private final DailyPlanRepository plans;
    private final DailyPlanItemRepository items;
    private final DailyPlanItemTagRepository itemTags;
    private final TagService tags;
    private final com.beehome.routine.repository.RoutineItemTagRepository routineItemTags;
    // Focused read query for resolution; routine writes remain in RoutineService.
    private final RoutineItemRepository routineItems;
    private final FamilyAuthorizationService authorization;
    private final FamilyMemberService members;
    private final FamilyService families;
    private final Clock clock;
    public DailyPlanService(DailyPlanRepository plans, DailyPlanItemRepository items, DailyPlanItemTagRepository itemTags,
            TagService tags, com.beehome.routine.repository.RoutineItemTagRepository routineItemTags, RoutineItemRepository routineItems,
            FamilyAuthorizationService authorization, FamilyMemberService members, FamilyService families, Clock clock) {
        this.plans = plans; this.items = items; this.itemTags=itemTags; this.tags=tags; this.routineItemTags=routineItemTags;
        this.routineItems = routineItems; this.authorization = authorization;
        this.members = members; this.families = families; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public ResolvedDailyPlan resolve(UUID user, UUID family, UUID member, LocalDate date) {
        var profile = members.requireActive(user, family, member, false);
        JsonFields.required(date);
        return resolved(user, family, profile, date);
    }
    @Transactional(readOnly = true)
    public List<DailyPlanItemResponse> listItems(UUID user, UUID family, UUID member, LocalDate date) {
        members.requireActive(user, family, member, false);
        JsonFields.required(date);
        return plans.findByFamilyIdAndFamilyMemberIdAndPlanDate(family, member, date)
                .map(plan -> itemResponses(family,allItems(plan.getId())))
                .orElseGet(List::of);
    }
    @Transactional
    public ResolvedDailyPlan note(UUID user, UUID family, UUID member, LocalDate date, DailyNoteRequest request) {
        var profile = editable(user, family, member, date);
        String note = JsonFields.text(request.note(), 2000, false);
        var existing = plans.findByFamilyIdAndFamilyMemberIdAndPlanDate(family, member, date);
        if (note != null || existing.isPresent()) {
            var plan = existing.orElseGet(() -> createPlan(family, member, date));
            plan.setNote(note, clock.instant());
        }
        return resolved(user, family, profile, date);
    }
    @Transactional
    public DailyPlanItemResponse createItem(UUID user, UUID family, UUID member, LocalDate date, CreateDailyPlanItemRequest request) {
        editable(user, family, member, date);
        tags.requireTags(user,family,request.tagIds());
        var plan = createPlan(family, member, date);
        var item = new DailyPlanItem(family,plan.getId(), request.title(), request.description(), request.scheduledTime(), request.sortOrder(), clock.instant());
        if (items.countByDailyPlanId(plan.getId()) >= 500) throw new InputException();
        ensureOrder(plan.getId(), item.getId(), item.getSortOrder());
        items.saveAndFlush(item);
        replaceTags(user,family,item.getId(),request.tagIds());
        return itemResponses(family,List.of(item)).getFirst();
    }
    @Transactional
    public DailyPlanItemResponse editItem(UUID user, UUID family, UUID member, LocalDate date, UUID id, PatchDailyPlanItemRequest request) {
        editable(user, family, member, date);
        var plan = requiredPlan(family, member, date);
        var item = item(plan, id);
        var fields = request.fields();
        if (fields.isEmpty()) throw new InputException();
        if(fields.contains("tagIds")) tags.requireTags(user,family,request.tagIds());
        item.edit(fields.contains("title") ? request.title() : item.getTitle(),
                fields.contains("description") ? request.description() : item.getDescription(),
                fields.contains("scheduledTime") ? request.scheduledTime() : item.getScheduledTime(),
                fields.contains("sortOrder") ? request.sortOrder() : Integer.valueOf(item.getSortOrder()), clock.instant());
        ensureOrder(plan.getId(), item.getId(), item.getSortOrder());
        if(fields.contains("tagIds")) replaceTags(user,family,id,request.tagIds());
        return itemResponses(family,List.of(item)).getFirst();
    }
    @Transactional
    public DailyPlanItemResponse setItemActive(UUID user, UUID family, UUID member, LocalDate date, UUID id, boolean active) {
        editable(user, family, member, date);
        var item = item(requiredPlan(family, member, date), id);
        item.setActive(active, clock.instant());
        return itemResponses(family,List.of(item)).getFirst();
    }
    @Transactional
    public List<DailyPlanItemResponse> reorder(UUID user, UUID family, UUID member, LocalDate date, ItemOrderRequest request) {
        editable(user, family, member, date);
        var all = allItems(requiredPlan(family, member, date).getId());
        var expected = all.stream().map(DailyPlanItem::getId).collect(Collectors.toSet());
        if (request.items() != null && request.items().stream().anyMatch(entry -> entry != null && entry.id() != null && !expected.contains(entry.id()))) {
            throw DailyPlanException.itemNotFound();
        }
        var order = request.validate(expected);
        var now = clock.instant();
        all.forEach(item -> item.reorder(order.get(item.getId()), now));
        return itemResponses(family,all.stream().sorted(Comparator.comparingInt(DailyPlanItem::getSortOrder)).toList());
    }
    private FamilyMemberResponse editable(UUID user, UUID family, UUID member, LocalDate date) {
        authorization.requireEditor(authorization.requireMembership(user, family));
        JsonFields.required(date);
        return members.requireActive(user, family, member, true);
    }
    private DailyPlan createPlan(UUID family, UUID member, LocalDate date) {
        plans.createIfAbsent(UUID.randomUUID(), family, member, date, clock.instant());
        return requiredPlan(family, member, date);
    }
    private DailyPlan requiredPlan(UUID family, UUID member, LocalDate date) {
        return plans.findByFamilyIdAndFamilyMemberIdAndPlanDate(family, member, date).orElseThrow(DailyPlanException::notFound);
    }
    private DailyPlanItem item(DailyPlan plan, UUID id) {
        return items.findByDailyPlanIdAndId(plan.getId(), id).orElseThrow(DailyPlanException::itemNotFound);
    }
    private void ensureOrder(UUID plan, UUID item, int order) {
        if (items.existsByDailyPlanIdAndSortOrderAndIdNot(plan, order, item)) throw new InputException();
    }
    private List<DailyPlanItem> allItems(UUID plan) {
        var result = items.findByDailyPlanIdOrderBySortOrderAscIdAsc(plan, PageRequest.of(0, 501));
        if (result.size() > 500) throw new InputException();
        return result;
    }
    private void replaceTags(UUID user,UUID family,UUID itemId,List<UUID> ids) {
        tags.requireTags(user,family,ids);
        itemTags.deleteByDailyPlanItemId(itemId);
        itemTags.flush();
        itemTags.saveAll(ids.stream().map(id -> new DailyPlanItemTag(family,itemId,id)).toList());
    }
    private List<DailyPlanItemResponse> itemResponses(UUID family,List<DailyPlanItem> rows) {
        if(rows.isEmpty()) return List.of();
        var links=itemTags.findByDailyPlanItemIdIn(rows.stream().map(DailyPlanItem::getId).toList());
        Map<UUID,TagSummary> summaries=tags.summaries(family,links.stream().map(DailyPlanItemTag::getTagId).collect(Collectors.toSet()));
        Map<UUID,List<TagSummary>> byItem=new HashMap<>();
        for(var link:links) byItem.computeIfAbsent(link.getDailyPlanItemId(),_ -> new ArrayList<>()).add(summaries.get(link.getTagId()));
        return rows.stream().map(row -> DailyPlanItemResponse.from(row,byItem.getOrDefault(row.getId(),List.of()).stream()
                .sorted(Comparator.comparing(TagSummary::name).thenComparing(TagSummary::id)).toList())).toList();
    }
    private ResolvedDailyPlan resolved(UUID user, UUID family, FamilyMemberResponse member, LocalDate date) {
        var recurring = routineItems.applicable(family, member.id(), date, date.getDayOfWeek(), PageRequest.of(0, 1001));
        if (recurring.size() > 1000) throw new InputException();
        var result = new ArrayList<ResolvedDailyPlan.Item>();
        var routineLinks=routineItemTags.findByRoutineItemIdIn(recurring.stream().map(com.beehome.routine.entity.RoutineItem::getId).toList());
        var plan = plans.findByFamilyIdAndFamilyMemberIdAndPlanDate(family, member.id(), date);
        var dailyRows=plan.isPresent() ? allItems(plan.get().getId()).stream().filter(DailyPlanItem::isActive).toList() : List.<DailyPlanItem>of();
        var dailyLinks=itemTags.findByDailyPlanItemIdIn(dailyRows.stream().map(DailyPlanItem::getId).toList());
        var tagIds=new HashSet<UUID>();
        routineLinks.forEach(link -> tagIds.add(link.getTagId()));
        dailyLinks.forEach(link -> tagIds.add(link.getTagId()));
        var summaries=tags.summaries(family,tagIds);
        Map<UUID,List<TagSummary>> assigned=new HashMap<>();
        routineLinks.forEach(link -> assigned.computeIfAbsent(link.getRoutineItemId(),_ -> new ArrayList<>()).add(summaries.get(link.getTagId())));
        dailyLinks.forEach(link -> assigned.computeIfAbsent(link.getDailyPlanItemId(),_ -> new ArrayList<>()).add(summaries.get(link.getTagId())));
        assigned.values().forEach(list -> list.sort(Comparator.comparing(TagSummary::name).thenComparing(TagSummary::id)));
        recurring.forEach(i -> result.add(new ResolvedDailyPlan.Item(ResolvedDailyPlan.Source.ROUTINE, i.getId(),
                i.getTitle(), i.getDescription(), i.getScheduledTime(), i.getSortOrder(),assigned.getOrDefault(i.getId(),List.of()))));
        if (plan.isPresent()) {
            dailyRows.forEach(i ->
                    result.add(new ResolvedDailyPlan.Item(ResolvedDailyPlan.Source.DAILY_PLAN, i.getId(),
                            i.getTitle(), i.getDescription(), i.getScheduledTime(), i.getSortOrder(),assigned.getOrDefault(i.getId(),List.of()))));
        }
        if (result.size() > 1000) throw new InputException();
        result.sort(Comparator.comparingInt(ResolvedDailyPlan.Item::sortOrder).thenComparing(ResolvedDailyPlan.Item::source)
                .thenComparing(i -> i.sourceId().toString()));
        return new ResolvedDailyPlan(date, families.get(user, family).timezone(),
                new ResolvedDailyPlan.Member(member.id(), member.name(), member.memberType()), plan.map(DailyPlan::getNote).orElse(null), result);
    }
}
