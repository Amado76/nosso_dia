package com.beehome.study.dto;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
public record StudySummary(LocalDate from, LocalDate to, long totalDurationSeconds, List<Subject> subjects) {
    public record Subject(UUID subjectId, long durationSeconds, long sessionCount) {}
}
