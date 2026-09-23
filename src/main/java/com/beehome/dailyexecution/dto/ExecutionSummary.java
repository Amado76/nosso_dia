package com.beehome.dailyexecution.dto;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(requiredProperties = {"total", "completed", "pending", "cancelled", "completionPercentage"})
public record ExecutionSummary(long total, long completed, long pending, long cancelled, @Schema(nullable = true) Double completionPercentage) {
    public static ExecutionSummary of(long completed, long pending, long cancelled) {
        return new ExecutionSummary(completed + pending + cancelled, completed, pending, cancelled,
                completed + pending == 0 ? null : 100.0 * completed / (completed + pending));
    }
}
