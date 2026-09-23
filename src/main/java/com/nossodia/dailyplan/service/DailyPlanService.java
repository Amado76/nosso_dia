package com.nossodia.dailyplan.service;

import com.nossodia.dailyplan.dto.*;
import com.nossodia.dailyplan.entity.*;
import com.nossodia.dailyplan.exception.DailyPlanException;
import com.nossodia.dailyplan.repository.*;
import com.nossodia.family.service.FamilyAuthorizationService;
import com.nossodia.family.service.FamilyService;
import com.nossodia.familymember.service.FamilyMemberService;
import com.nossodia.familymember.dto.FamilyMemberResponse;
import com.nossodia.routine.repository.RoutineItemRepository;
import com.nossodia.shared.dto.*;
import com.nossodia.shared.exception.InputException;
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
    // Focused read query for resolution; routine writes remain in RoutineService.
    private final RoutineItemRepository routineItems;
    private final FamilyAuthorizationService authorization;
    private final FamilyMemberService members;
    private final FamilyService families;
    private final Clock clock;
    public DailyPlanService(DailyPlanRepository plans, DailyPlanItemRepository items, RoutineItemRepository routineItems,
            FamilyAuthorizationService authorization, FamilyMemberService members, FamilyService families, Clock clock) {
        this.plans = plans; this.items = items; this.routineItems = routineItems; this.authorization = authorization;
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
                .map(plan -> allItems(plan.getId()).stream().map(DailyPlanItemResponse::from).toList())
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
        var plan = createPlan(family, member, date);
        var item = new DailyPlanItem(plan.getId(), request.title(), request.description(), request.scheduledTime(), request.sortOrder(), clock.instant());
        if (items.countByDailyPlanId(plan.getId()) >= 500) throw new InputException();
        ensureOrder(plan.getId(), item.getId(), item.getSortOrder());
        return DailyPlanItemResponse.from(items.save(item));
    }
    @Transactional
    public DailyPlanItemResponse editItem(UUID user, UUID family, UUID member, LocalDate date, UUID id, PatchDailyPlanItemRequest request) {
        editable(user, family, member, date);
        var plan = requiredPlan(family, member, date);
        var item = item(plan, id);
        var fields = request.fields();
        if (fields.isEmpty()) throw new InputException();
        item.edit(fields.contains("title") ? request.title() : item.getTitle(),
                fields.contains("description") ? request.description() : item.getDescription(),
                fields.contains("scheduledTime") ? request.scheduledTime() : item.getScheduledTime(),
                fields.contains("sortOrder") ? request.sortOrder() : Integer.valueOf(item.getSortOrder()), clock.instant());
        ensureOrder(plan.getId(), item.getId(), item.getSortOrder());
        return DailyPlanItemResponse.from(item);
    }
    @Transactional
    public DailyPlanItemResponse setItemActive(UUID user, UUID family, UUID member, LocalDate date, UUID id, boolean active) {
        editable(user, family, member, date);
        var item = item(requiredPlan(family, member, date), id);
        item.setActive(active, clock.instant());
        return DailyPlanItemResponse.from(item);
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
        return all.stream().sorted(Comparator.comparingInt(DailyPlanItem::getSortOrder))
                .map(DailyPlanItemResponse::from).toList();
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
    private ResolvedDailyPlan resolved(UUID user, UUID family, FamilyMemberResponse member, LocalDate date) {
        var recurring = routineItems.applicable(family, member.id(), date, date.getDayOfWeek(), PageRequest.of(0, 1001));
        if (recurring.size() > 1000) throw new InputException();
        var result = new ArrayList<ResolvedDailyPlan.Item>();
        recurring.forEach(i -> result.add(new ResolvedDailyPlan.Item(ResolvedDailyPlan.Source.ROUTINE, i.getId(),
                i.getTitle(), i.getDescription(), i.getScheduledTime(), i.getSortOrder())));
        var plan = plans.findByFamilyIdAndFamilyMemberIdAndPlanDate(family, member.id(), date);
        if (plan.isPresent()) {
            allItems(plan.get().getId()).stream().filter(DailyPlanItem::isActive).forEach(i ->
                    result.add(new ResolvedDailyPlan.Item(ResolvedDailyPlan.Source.DAILY_PLAN, i.getId(),
                            i.getTitle(), i.getDescription(), i.getScheduledTime(), i.getSortOrder())));
        }
        if (result.size() > 1000) throw new InputException();
        result.sort(Comparator.comparingInt(ResolvedDailyPlan.Item::sortOrder).thenComparing(ResolvedDailyPlan.Item::source)
                .thenComparing(i -> i.sourceId().toString()));
        return new ResolvedDailyPlan(date, families.get(user, family).timezone(),
                new ResolvedDailyPlan.Member(member.id(), member.name(), member.memberType()), plan.map(DailyPlan::getNote).orElse(null), result);
    }
}
