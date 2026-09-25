package com.beehome.history.dto;

import java.time.LocalDate;
import java.util.List;

public record HistoryCalendar(int year, int month, List<Day> days) {
    public record Day(LocalDate date, boolean hasRoutine, boolean hasStudies, boolean hasReading, boolean hasPhotos) {}
}
