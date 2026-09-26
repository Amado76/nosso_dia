package com.beehome.routine.dto;
import com.beehome.routine.entity.RoutineItem;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.UUID;
import java.util.List;
import com.beehome.tag.dto.TagSummary;
@Schema(requiredProperties = {"id", "familyMemberId", "title", "description", "scheduledTime", "sortOrder", "active", "createdAt", "updatedAt"})
public record RoutineItemResponse(UUID id, UUID familyMemberId, String title, @Schema(nullable = true) String description,
        @JsonFormat(pattern = "HH:mm") @Schema(type = "string", nullable = true, example = "08:30") LocalTime scheduledTime,
        int sortOrder, boolean active, Instant createdAt, Instant updatedAt, List<TagSummary> tags) {
    public static RoutineItemResponse from(RoutineItem i) {
        return from(i,List.of());
    }
    public static RoutineItemResponse from(RoutineItem i,List<TagSummary> tags) {
        return new RoutineItemResponse(i.getId(), i.getFamilyMemberId(), i.getTitle(), i.getDescription(), i.getScheduledTime(),
                i.getSortOrder(), i.isActive(), i.getCreatedAt(), i.getUpdatedAt(), tags);
    }
}
