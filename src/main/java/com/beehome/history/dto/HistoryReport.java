package com.beehome.history.dto;

import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HistoryReport(UUID childId, LocalDate from, LocalDate to, Routine routine,
        Studies studies, Reading reading, Photos photos) {
    public record Routine(long days, long plannedItems, long completedItems) {}
    public record Studies(long sessions, BigDecimal totalMinutes, List<Subject> subjects) {}
    public record Subject(UUID subjectId, BigDecimal minutes) {}
    @Schema(name = "HistoryReadingTotals", description = "Live reading summary: distinct books with sessions and completed journeys in the period")
    public record Reading(long sessions, long totalMinutes, long pagesRead, long books, long booksCompleted) {}
    public record Photos(long records, long images) {}
}
