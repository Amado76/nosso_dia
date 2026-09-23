package com.beehome.dailyexecution.entity;
import jakarta.persistence.*;
import java.time.*;
import java.util.*;
@Entity
@Table(name = "daily_executions", schema = "beehome")
public class DailyExecution {
    @Id private UUID id;
    @Column(nullable = false) private UUID familyId;
    @Column(nullable = false) private UUID familyMemberId;
    @Column(nullable = false) private LocalDate executionDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ExecutionStatus status;
    @Column(nullable = false) private boolean reopened;
    @Column(length = 2000) private String note;
    private Instant finalizedAt;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    protected DailyExecution() {}
    public boolean closed(LocalDate today) { return status == ExecutionStatus.FINALIZED || executionDate.isBefore(today) && !reopened; }
    public void close(Instant now) {
        if (status == ExecutionStatus.FINALIZED) return;
        status = ExecutionStatus.FINALIZED; reopened = false; finalizedAt = now; updatedAt = now;
    }
    public void reopen(Instant now) {
        if (status == ExecutionStatus.OPEN && reopened) return;
        status = ExecutionStatus.OPEN; reopened = true; finalizedAt = null; updatedAt = now;
    }
    public void note(String value, Instant now) { if (!Objects.equals(note, value)) { note = value; touch(now); } }
    public void touch(Instant now) { updatedAt = now; }
    public UUID getId() { return id; }
    public LocalDate getExecutionDate() { return executionDate; }
    public ExecutionStatus getStatus() { return status; }
    public boolean isReopened() { return reopened; }
    public String getNote() { return note; }
    public Instant getFinalizedAt() { return finalizedAt; }
}
