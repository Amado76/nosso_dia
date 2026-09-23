package com.beehome.dailyexecution.dto;
import com.beehome.dailyexecution.entity.ExecutionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.*;
@Schema(requiredProperties = {"items", "page", "size", "hasNext"})
public record ExecutionPage(List<Day> items, int page, int size, boolean hasNext) {
    @Schema(name = "ExecutionDay", requiredProperties = {"id", "date", "status", "reopened", "finalizedAt", "summary"})
    public record Day(UUID id, LocalDate date, ExecutionStatus status, boolean reopened,
            @Schema(nullable = true) Instant finalizedAt, ExecutionSummary summary) {}
}
