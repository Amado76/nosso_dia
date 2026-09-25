package com.beehome.history.dto;

import java.time.LocalDate;
import java.util.List;

public record HistoryPage(List<Day> items, int page, int size, boolean hasNext) {
    public record Day(LocalDate date, boolean hasRoutine, int plannedItems, int completedItems,
            int studySessions, long studyDurationSeconds, int photoRecords, int images) {}
}
