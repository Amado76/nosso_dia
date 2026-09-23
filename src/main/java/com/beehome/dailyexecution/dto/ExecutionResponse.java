package com.beehome.dailyexecution.dto;
import com.beehome.dailyexecution.entity.*;
import com.beehome.dailyplan.dto.ResolvedDailyPlan;
import com.beehome.familymember.entity.MemberType;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.*;
@Schema(requiredProperties = {"id", "date", "status", "reopened", "finalizedAt", "member", "note", "items", "summary"})
public record ExecutionResponse(UUID id, LocalDate date, ExecutionStatus status, boolean reopened,
        @Schema(nullable = true) Instant finalizedAt, Member member, @Schema(nullable = true) String note, List<Item> items, ExecutionSummary summary) {
    @Schema(name = "ExecutionMember", requiredProperties = {"id", "name", "memberType", "color"})
    public record Member(UUID id, String name, MemberType memberType, @Schema(nullable = true) String color) {}
    @Schema(name = "ExecutionItem", requiredProperties = {"id", "sourceType", "sourceId", "title", "description", "scheduledTime", "sortOrder", "status", "completedAt", "completedByUserId"})
    public record Item(UUID id, ResolvedDailyPlan.Source sourceType, @Schema(nullable = true) UUID sourceId, String title,
            @Schema(nullable = true) String description,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", nullable = true, example = "08:30") LocalTime scheduledTime,
            int sortOrder, ItemStatus status, @Schema(nullable = true) Instant completedAt, @Schema(nullable = true) UUID completedByUserId) {
        public static Item from(DailyExecutionItem i) { return new Item(i.getId(), i.getSourceType(), i.getSourceId(), i.getTitle(), i.getDescription(), i.getScheduledTime(), i.getSortOrder(), i.getStatus(), i.getCompletedAt(), i.getCompletedByUserId()); }
    }
}
