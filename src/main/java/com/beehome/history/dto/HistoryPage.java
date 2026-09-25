package com.beehome.history.dto;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HistoryPage(List<Day> items, int page, int size, boolean hasNext) {
    @Schema(name = "HistoryPeriodDay")
    public record Day(LocalDate date, boolean hasRoutine, int plannedItems, int completedItems,
            int studySessions, long studyDurationSeconds, long readingSessions, int photoRecords, int images) {}
}
