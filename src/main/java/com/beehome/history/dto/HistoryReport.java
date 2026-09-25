package com.beehome.history.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HistoryReport(UUID childId, LocalDate from, LocalDate to, Routine routine,
        Studies studies, Reading reading, Photos photos) {
    public record Routine(long days, long plannedItems, long completedItems) {}
    public record Studies(long sessions, BigDecimal totalMinutes, List<Subject> subjects) {}
    public record Subject(UUID subjectId, BigDecimal minutes) {}
    public record Reading(long sessions, long distinctBooks, long pagesRead) {}
    public record Photos(long records, long images) {}
}
