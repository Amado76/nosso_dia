package com.beehome.dailyexecution.entity;
import com.beehome.dailyplan.dto.ResolvedDailyPlan;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity
@Table(name = "daily_execution_items", schema = "beehome")
public class DailyExecutionItem {
    @Id private UUID id;
    @Column(nullable = false) private UUID executionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ResolvedDailyPlan.Source sourceType;
    private UUID sourceId;
    @Column(nullable = false, length = 120) private String title;
    @Column(length = 2000) private String description;
    private LocalTime scheduledTime;
    @Column(nullable = false) private int sortOrder;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ItemStatus status;
    private Instant completedAt;
    private UUID completedByUserId;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected DailyExecutionItem() {}
    public DailyExecutionItem(UUID execution, ResolvedDailyPlan.Item item, Instant now) {
        id = UUID.randomUUID(); executionId = execution; sourceType = item.source(); sourceId = item.sourceId();
        status = ItemStatus.PENDING; createdAt = now; updatedAt = now; synchronize(item, now);
    }
    public void synchronize(ResolvedDailyPlan.Item item, Instant now) {
        if (status == ItemStatus.COMPLETED) return;
        if (status != ItemStatus.PENDING || !Objects.equals(title, item.title()) || !Objects.equals(description, item.description())
                || !Objects.equals(scheduledTime, item.scheduledTime()) || sortOrder != item.sortOrder()) updatedAt = now;
        title = item.title(); description = item.description(); scheduledTime = item.scheduledTime(); sortOrder = item.sortOrder(); status = ItemStatus.PENDING;
    }
    public void cancel(Instant now) { if (status == ItemStatus.PENDING) { status = ItemStatus.CANCELLED; updatedAt = now; } }
    public boolean complete(boolean completed, UUID actor, Instant now) {
        if (status == ItemStatus.CANCELLED) throw com.beehome.dailyexecution.exception.DailyExecutionException.conflict();
        var next = completed ? ItemStatus.COMPLETED : ItemStatus.PENDING;
        if (status == next) return false;
        status = next; completedAt = completed ? now : null; completedByUserId = completed ? actor : null; updatedAt = now; return true;
    }
    public UUID getId() { return id; }
    public UUID getExecutionId() { return executionId; }
    public ResolvedDailyPlan.Source getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalTime getScheduledTime() { return scheduledTime; }
    public int getSortOrder() { return sortOrder; }
    public ItemStatus getStatus() { return status; }
    public Instant getCompletedAt() { return completedAt; }
    public UUID getCompletedByUserId() { return completedByUserId; }
}
