package com.beehome.routine.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import com.beehome.shared.dto.JsonFields;
import com.beehome.shared.exception.InputException;

@Entity
@Table(name = "routines", schema = "beehome")
public class Routine {
    @Id private UUID id;
    @Column(name = "family_id", nullable = false) private UUID familyId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false) private boolean active;
    private LocalDate startDate;
    private LocalDate endDate;
    @ElementCollection
    @CollectionTable(name = "routine_days", schema = "beehome", joinColumns = @JoinColumn(name = "routine_id"))
    @Column(name = "day_of_week", nullable = false, length = 9)
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> daysOfWeek = new HashSet<>();
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected Routine() {}
    public Routine(UUID familyId, String name, Set<DayOfWeek> days, LocalDate start, LocalDate end, Instant now) {
        id = UUID.randomUUID(); this.familyId = familyId; active = true; createdAt = now;
        edit(name, days, start, end, now);
    }
    public void edit(String name, Set<DayOfWeek> days, LocalDate start, LocalDate end, Instant now) {
        name = JsonFields.text(name, 120, true);
        if (days == null || days.isEmpty() || days.stream().anyMatch(Objects::isNull) || (start != null && end != null && end.isBefore(start))) throw new InputException();
        if (!Objects.equals(this.name, name) || !daysOfWeek.equals(days) || !Objects.equals(startDate, start) || !Objects.equals(endDate, end)) {
            this.name = name; daysOfWeek.clear(); daysOfWeek.addAll(days); startDate = start; endDate = end; updatedAt = now;
        }
    }
    public void setActive(boolean value, Instant now) { if (active != value) { active = value; updatedAt = now; } }
    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public Set<DayOfWeek> getDaysOfWeek() { return Set.copyOf(daysOfWeek); }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
