package com.beehome.history.dto;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record HistoryCalendar(int year, int month, List<Day> days) {
    @Schema(name = "HistoryCalendarDay")
    public record Day(LocalDate date, boolean hasRoutine, boolean hasStudies, boolean hasReading, boolean hasPhotos) {}
}
