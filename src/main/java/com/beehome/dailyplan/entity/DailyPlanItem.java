package com.beehome.dailyplan.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import com.beehome.shared.dto.JsonFields;
import com.beehome.shared.exception.InputException;

@Entity
@Table(name = "daily_plan_items", schema = "beehome")
public class DailyPlanItem {
    @Id private UUID id;
    @Column(nullable = false) private UUID dailyPlanId;
    @Column(nullable = false, length = 120) private String title;
    @Column(length = 2000) private String description;
    private LocalTime scheduledTime;
    @Column(nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected DailyPlanItem() {}
    public DailyPlanItem(UUID dailyPlanId, String title, String description, LocalTime time, Integer order, Instant now) {
        id = UUID.randomUUID(); this.dailyPlanId = dailyPlanId; 
        active = true; createdAt = now; edit(title, description, time, order, now);
    }
    public void edit(String title, String description, LocalTime time, Integer order, Instant now) {
        title = JsonFields.text(title, 120, true);
        description = JsonFields.text(description, 2000, false);
        if (order == null || order < 0 || (time != null && (time.getSecond() != 0 || time.getNano() != 0))) throw new InputException();
        if (!Objects.equals(this.title, title) || !Objects.equals(this.description, description) || !Objects.equals(scheduledTime, time) || sortOrder != order) {
            this.title = title; this.description = description; scheduledTime = time; sortOrder = order; updatedAt = now;
        }
    }
    public void reorder(int order, Instant now) { if (order < 0) throw new InputException(); if (sortOrder != order) { sortOrder = order; updatedAt = now; } }
    public void setActive(boolean value, Instant now) { if (active != value) { active = value; updatedAt = now; } }
    public UUID getId() { return id; }
    public UUID getDailyPlanId() { return dailyPlanId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalTime getScheduledTime() { return scheduledTime; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
