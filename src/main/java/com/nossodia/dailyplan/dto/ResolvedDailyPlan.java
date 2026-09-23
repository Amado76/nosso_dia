package com.nossodia.dailyplan.dto;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import com.nossodia.familymember.entity.MemberType;
import java.time.*;
import java.util.*;
@Schema(requiredProperties = {"date", "timezone", "member", "note", "items"})
public record ResolvedDailyPlan(LocalDate date, String timezone, Member member, @Schema(nullable = true) String note, List<Item> items) {
    @Schema(requiredProperties = {"id", "name", "memberType"})
    public record Member(UUID id, String name, MemberType memberType) {}
    public enum Source { ROUTINE, DAILY_PLAN }
    @Schema(requiredProperties = {"source", "sourceId", "title", "description", "scheduledTime", "sortOrder"})
    public record Item(Source source, UUID sourceId, String title, @Schema(nullable = true) String description,
            @JsonFormat(pattern = "HH:mm") @Schema(type = "string", nullable = true, example = "08:30") LocalTime scheduledTime, int sortOrder) {}
}
