package com.beehome.dailyplan.dto;
import com.beehome.dailyplan.entity.DailyPlanItem;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.UUID;
@Schema(requiredProperties = {"id", "title", "description", "scheduledTime", "sortOrder", "active", "createdAt", "updatedAt"})
public record DailyPlanItemResponse(UUID id, String title, @Schema(nullable = true) String description,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string", nullable = true, example = "08:30") LocalTime scheduledTime,
        int sortOrder, boolean active, Instant createdAt, Instant updatedAt) {
    public static DailyPlanItemResponse from(DailyPlanItem i) {
        return new DailyPlanItemResponse(i.getId(), i.getTitle(), i.getDescription(), i.getScheduledTime(),
                i.getSortOrder(), i.isActive(), i.getCreatedAt(), i.getUpdatedAt());
    }
}
