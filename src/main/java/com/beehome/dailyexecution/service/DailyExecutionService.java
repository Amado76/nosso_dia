package com.beehome.dailyexecution.service;

import com.beehome.dailyexecution.dto.*;
import com.beehome.dailyexecution.entity.*;
import com.beehome.dailyexecution.exception.*;
import com.beehome.dailyexecution.repository.*;
import com.beehome.dailyplan.service.DailyPlanService;
import com.beehome.family.service.*;
import com.beehome.familymember.dto.FamilyMemberResponse;
import com.beehome.familymember.service.FamilyMemberService;
import com.beehome.shared.exception.InputException;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyExecutionService {
    private final DailyExecutionRepository executions;
    private final DailyExecutionItemRepository items;
    private final DailyPlanService planning;
    private final FamilyAuthorizationService authorization;
    private final FamilyMemberService members;
    private final FamilyService families;
    private final Clock clock;

    public DailyExecutionService(DailyExecutionRepository executions, DailyExecutionItemRepository items,
            DailyPlanService planning, FamilyAuthorizationService authorization, FamilyMemberService members,
            FamilyService families, Clock clock) {
        this.executions = executions; this.items = items; this.planning = planning; this.authorization = authorization;
        this.members = members; this.families = families; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ExecutionResponse get(UUID user, UUID family, UUID member, LocalDate date) {
        var profile = members.get(user, family, member);
        var execution = executions.findByFamilyIdAndFamilyMemberIdAndExecutionDate(family, member, date)
                .orElseThrow(DailyExecutionException::missing);
        return response(execution, profile, today(user, family), items.findByExecutionId(execution.getId()));
    }

    @Transactional
    public ExecutionResponse materialize(UUID user, UUID family, UUID member, LocalDate date) {
        var profile = members.get(user, family, member);
        var today = today(user, family);
        rejectFuture(date, today);
        if (date.equals(today) && profile.active()) {
            // Match planning's member lock order, and serialize creation with deactivation.
            profile = members.requireActive(user, family, member, true);
            executions.createIfAbsent(UUID.randomUUID(), family, member, date, clock.instant());
        }
        var execution = locked(family, member, date);
        closeStale(execution, today);
        var snapshots = items.findByExecutionId(execution.getId());
        if (profile.active() && !execution.closed(today) && !execution.isReopened() && date.equals(today)) {
            var plan = planning.resolve(user, family, member, date);
            var bySource = new HashMap<SourceKey, DailyExecutionItem>();
            snapshots.forEach(i -> bySource.put(new SourceKey(i.getSourceType(), i.getSourceId()), i));
            var seen = new HashSet<SourceKey>();
            for (var source : plan.items()) {
                var key = new SourceKey(source.source(), source.sourceId());
                seen.add(key);
                var existing = bySource.get(key);
                if (existing == null) {
                    // Bound accumulated snapshots even when planning sources churn during the day.
                    if (snapshots.size() >= 2000) throw new InputException();
                    snapshots.add(items.save(new DailyExecutionItem(execution.getId(), source, clock.instant())));
                } else existing.synchronize(source, clock.instant());
            }
            snapshots.stream().filter(i -> !seen.contains(new SourceKey(i.getSourceType(), i.getSourceId())))
                    .forEach(i -> i.cancel(clock.instant()));
            execution.note(plan.note(), clock.instant());
        }
        return response(execution, profile, today, snapshots);
    }

    @Transactional(noRollbackFor = FinalizedExecutionException.class)
    public ExecutionResponse complete(UUID user, UUID family, UUID member, LocalDate date, UUID item, boolean completed) {
        var profile = members.get(user, family, member);
        var today = today(user, family);
        rejectFuture(date, today);
        var execution = locked(family, member, date);
        var snapshots = items.findByExecutionId(execution.getId());
        var target = snapshots.stream().filter(i -> i.getId().equals(item)).findFirst()
                .orElseThrow(DailyExecutionException::itemMissing);
        closeStale(execution, today);
        if (execution.closed(today)) throw new FinalizedExecutionException();
        if (target.complete(completed, user, clock.instant())) execution.touch(clock.instant());
        return response(execution, profile, today, snapshots);
    }

    @Transactional
    public ExecutionResponse finalizeExecution(UUID user, UUID family, UUID member, LocalDate date) {
        var profile = members.get(user, family, member);
        authorization.requireEditor(authorization.requireMembership(user, family));
        var today = today(user, family);
        rejectFuture(date, today);
        var execution = locked(family, member, date);
        execution.close(clock.instant());
        return response(execution, profile, today, items.findByExecutionId(execution.getId()));
    }

    @Transactional
    public ExecutionResponse reopen(UUID user, UUID family, UUID member, LocalDate date) {
        var profile = members.get(user, family, member);
        authorization.requireEditor(authorization.requireMembership(user, family));
        var today = today(user, family);
        rejectFuture(date, today);
        var execution = locked(family, member, date);
        if (!execution.closed(today) && !execution.isReopened()) throw DailyExecutionException.conflict();
        execution.reopen(clock.instant());
        return response(execution, profile, today, items.findByExecutionId(execution.getId()));
    }

    @Transactional(readOnly = true)
    public ExecutionPage history(UUID user, UUID family, UUID member, LocalDate from, LocalDate to, int page, int size) {
        members.get(user, family, member);
        if (from == null || to == null || from.isAfter(to) || page < 0 || size < 1 || size > 100
                || (long) page * size > Integer.MAX_VALUE - 1) throw new InputException();
        var today = today(user, family);
        var slice = executions.history(family, member, from, to, PageRequest.of(page, size));
        var counts = new HashMap<UUID, long[]>();
        if (!slice.isEmpty()) {
            items.counts(slice.getContent().stream().map(DailyExecution::getId).toList()).forEach(c ->
                    counts.computeIfAbsent(c.getExecutionId(), ignored -> new long[3])[c.getStatus().ordinal()] = c.getCount());
        }
        return new ExecutionPage(slice.getContent().stream().map(e -> {
            var c = counts.getOrDefault(e.getId(), new long[3]);
            return new ExecutionPage.Day(e.getId(), e.getExecutionDate(), effectiveStatus(e, today), e.isReopened(),
                    e.getFinalizedAt(), ExecutionSummary.of(c[ItemStatus.COMPLETED.ordinal()], c[ItemStatus.PENDING.ordinal()], c[ItemStatus.CANCELLED.ordinal()]));
        }).toList(), page, size, slice.hasNext());
    }

    private DailyExecution locked(UUID family, UUID member, LocalDate date) {
        return executions.lock(family, member, date).orElseThrow(DailyExecutionException::missing);
    }
    private LocalDate today(UUID user, UUID family) {
        return LocalDate.now(clock.withZone(ZoneId.of(families.get(user, family).timezone())));
    }
    private void closeStale(DailyExecution execution, LocalDate today) {
        if (execution.closed(today)) execution.close(clock.instant());
    }
    private static void rejectFuture(LocalDate date, LocalDate today) {
        if (date.isAfter(today)) throw DailyExecutionException.future();
    }
    private static ExecutionStatus effectiveStatus(DailyExecution execution, LocalDate today) {
        return execution.closed(today) ? ExecutionStatus.FINALIZED : ExecutionStatus.OPEN;
    }
    private static ExecutionResponse response(DailyExecution e, FamilyMemberResponse member, LocalDate today, List<DailyExecutionItem> items) {
        var sorted = items.stream().sorted(Comparator.comparingInt(DailyExecutionItem::getSortOrder)
                .thenComparing(DailyExecutionItem::getSourceType)
                .thenComparing(i -> i.getSourceId() == null ? "" : i.getSourceId().toString())
                .thenComparing(i -> i.getId().toString())).map(ExecutionResponse.Item::from).toList();
        long completed = items.stream().filter(i -> i.getStatus() == ItemStatus.COMPLETED).count();
        long pending = items.stream().filter(i -> i.getStatus() == ItemStatus.PENDING).count();
        return new ExecutionResponse(e.getId(), e.getExecutionDate(), effectiveStatus(e, today), e.isReopened(), e.getFinalizedAt(),
                new ExecutionResponse.Member(member.id(), member.name(), member.memberType(), member.color()), e.getNote(), sorted,
                ExecutionSummary.of(completed, pending, items.size() - completed - pending));
    }
    private record SourceKey(com.beehome.dailyplan.dto.ResolvedDailyPlan.Source type, UUID id) {}
}
