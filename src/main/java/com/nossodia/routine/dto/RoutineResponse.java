package com.nossodia.routine.dto;
import com.nossodia.routine.entity.Routine;
import java.time.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;
@Schema(requiredProperties = {"id", "familyId", "name", "active", "daysOfWeek", "startDate", "endDate", "createdAt", "updatedAt", "items"})
public record RoutineResponse(UUID id, UUID familyId, String name, boolean active, Set<DayOfWeek> daysOfWeek,
        @Schema(nullable = true) LocalDate startDate, @Schema(nullable = true) LocalDate endDate, Instant createdAt, Instant updatedAt, List<RoutineItemResponse> items) {
    public static RoutineResponse from(Routine r, List<RoutineItemResponse> items) {
        return new RoutineResponse(r.getId(), r.getFamilyId(), r.getName(), r.isActive(), r.getDaysOfWeek(),
                r.getStartDate(), r.getEndDate(), r.getCreatedAt(), r.getUpdatedAt(), items);
    }
}
