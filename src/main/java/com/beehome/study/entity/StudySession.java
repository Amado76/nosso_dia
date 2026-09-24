package com.beehome.study.entity;

import com.beehome.study.exception.StudyException;
import jakarta.persistence.*;
import java.time.*;
import java.util.UUID;

@Entity
@Table(name = "study_sessions", schema = "beehome")
public class StudySession {
    public static final int MAX_SECONDS = 31_536_000;
    @Id private UUID id;
    @Column(nullable = false) private UUID familyId;
    @Column(nullable = false) private UUID familyMemberId;
    private UUID subjectId;
    private UUID dailyExecutionItemId;
    @Column(length = 120) private String subjectNameSnapshot;
    @Column(name = "session_date", nullable = false) private LocalDate date;
    @Column(length = 120) private String title;
    @Column(columnDefinition = "text") private String notes;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private EntryMode entryMode;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private StudyStatus status;
    private Instant startedAt;
    private Instant currentRunStartedAt;
    private Instant endedAt;
    @Column(nullable = false) private int accumulatedDurationSeconds;
    @Column(nullable = false) private UUID createdByUserId;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long version;
    protected StudySession() {}
    private StudySession(UUID family, UUID member, UUID subject, String snapshot, UUID item,
            LocalDate date, String title, String notes, UUID actor, Instant now) {
        id = UUID.randomUUID(); familyId = family; familyMemberId = member; subjectId = subject;
        subjectNameSnapshot = snapshot; dailyExecutionItemId = item; this.date = date;
        this.title = title; this.notes = notes; createdByUserId = actor; createdAt = now; updatedAt = now;
    }
    public static StudySession timer(UUID family, UUID member, UUID subject, String snapshot, UUID item,
            LocalDate date, String title, String notes, UUID actor, Instant now) {
        var session = new StudySession(family, member, subject, snapshot, item, date, title, notes, actor, now);
        session.entryMode = EntryMode.TIMER; session.status = StudyStatus.RUNNING;
        session.startedAt = now; session.currentRunStartedAt = now; return session;
    }
    public static StudySession manual(UUID family, UUID member, UUID subject, String snapshot, UUID item,
            LocalDate date, String title, String notes, int seconds, UUID actor, Instant now) {
        checkSeconds(seconds);
        var session = new StudySession(family, member, subject, snapshot, item, date, title, notes, actor, now);
        session.entryMode = EntryMode.MANUAL; session.status = StudyStatus.COMPLETED;
        session.accumulatedDurationSeconds = seconds; return session;
    }
    public void pause(Instant now) {
        if (status != StudyStatus.RUNNING) throw StudyException.state();
        addInterval(now); currentRunStartedAt = null; status = StudyStatus.PAUSED; updatedAt = now;
    }
    public void resume(Instant now) {
        if (status != StudyStatus.PAUSED) throw StudyException.state();
        currentRunStartedAt = now; status = StudyStatus.RUNNING; updatedAt = now;
    }
    public void finish(Instant now) {
        if (status != StudyStatus.RUNNING && status != StudyStatus.PAUSED) throw StudyException.state();
        if (status == StudyStatus.RUNNING) addInterval(now);
        currentRunStartedAt = null; endedAt = now; status = StudyStatus.COMPLETED; updatedAt = now;
    }
    public void correct(UUID subject, String snapshot, UUID item, LocalDate date, String title, String notes, Integer seconds, Instant now) {
        if (status != StudyStatus.COMPLETED) throw StudyException.state();
        if (entryMode == EntryMode.TIMER && (date != null || seconds != null)) throw StudyException.state();
        subjectId = subject; subjectNameSnapshot = snapshot; dailyExecutionItemId = item;
        if (date != null) this.date = date;
        if (seconds != null) { checkSeconds(seconds); accumulatedDurationSeconds = seconds; }
        this.title = title; this.notes = notes; updatedAt = now;
    }
    public void voidSession(Instant now) {
        if (status == StudyStatus.VOIDED) return;
        // Voiding must release abandoned timers even when their duration exceeds storage bounds.
        if (status == StudyStatus.RUNNING) accumulatedDurationSeconds = (int) Math.min(MAX_SECONDS, totalRunningSeconds(now));
        if (entryMode == EntryMode.TIMER && endedAt == null) endedAt = now;
        currentRunStartedAt = null; status = StudyStatus.VOIDED; updatedAt = now;
    }
    private void addInterval(Instant now) {
        long total = totalRunningSeconds(now);
        if (total > MAX_SECONDS) throw StudyException.state();
        accumulatedDurationSeconds = (int) total;
    }
    private long totalRunningSeconds(Instant now) {
        long elapsed = Duration.between(currentRunStartedAt, now).getSeconds();
        if (elapsed < 0) throw StudyException.state();
        return elapsed + accumulatedDurationSeconds;
    }
    public static void checkSeconds(int seconds) { if (seconds < 0 || seconds > MAX_SECONDS) throw new com.beehome.shared.exception.InputException(); }
    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public UUID getFamilyMemberId() { return familyMemberId; }
    public UUID getSubjectId() { return subjectId; }
    public UUID getDailyExecutionItemId() { return dailyExecutionItemId; }
    public String getSubjectNameSnapshot() { return subjectNameSnapshot; }
    public LocalDate getDate() { return date; }
    public String getTitle() { return title; }
    public String getNotes() { return notes; }
    public EntryMode getEntryMode() { return entryMode; }
    public StudyStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCurrentRunStartedAt() { return currentRunStartedAt; }
    public Instant getEndedAt() { return endedAt; }
    public int getAccumulatedDurationSeconds() { return accumulatedDurationSeconds; }
    public UUID getCreatedByUserId() { return createdByUserId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
