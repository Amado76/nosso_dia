package com.nossodia.dailyplan.entity;

import jakarta.persistence.*;
import java.time.*;
import java.util.*;
import com.nossodia.shared.dto.JsonFields;

@Entity
@Table(name = "daily_plans", schema = "nosso_dia")
public class DailyPlan {
    @Id private UUID id;
    @Column(nullable = false) private UUID familyId;
    @Column(nullable = false) private UUID familyMemberId;
    @Column(nullable = false) private LocalDate planDate;
    @Column(length = 2000) private String note;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected DailyPlan() {}
    public void setNote(String note, Instant now) {
        note = JsonFields.text(note, 2000, false);
        if (!Objects.equals(this.note, note)) { this.note = note; updatedAt = now; }
    }
    public UUID getId() { return id; }
    public String getNote() { return note; }
}
