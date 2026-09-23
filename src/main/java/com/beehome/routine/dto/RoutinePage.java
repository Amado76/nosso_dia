package com.beehome.routine.dto;
import com.beehome.routine.entity.Routine;
import java.time.*;
import java.util.*;
public record RoutinePage(List<Row> items, int page, int size, boolean hasNext) {
    public record Row(UUID id, String name, boolean active, LocalDate startDate, LocalDate endDate, Instant createdAt, Instant updatedAt) {
        public static Row from(Routine r) { return new Row(r.getId(), r.getName(), r.isActive(), r.getStartDate(), r.getEndDate(), r.getCreatedAt(), r.getUpdatedAt()); }
    }
}
