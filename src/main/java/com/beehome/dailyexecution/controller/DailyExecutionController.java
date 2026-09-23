package com.beehome.dailyexecution.controller;
import com.beehome.dailyexecution.dto.*;
import com.beehome.dailyexecution.service.DailyExecutionService;
import com.beehome.shared.dto.JsonFields;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/families/{familyId}/members/{memberId}/executions")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Invalid date, future execution, pagination, range, or snapshot limit", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "401", description = "Bearer authentication required", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "403", description = "OWNER or ADMIN required for finalize and reopen", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "Family, member, execution, or item missing or inaccessible", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "409", description = "Execution finalized, cancelled item, or conflicting transition; reload", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class DailyExecutionController {
    private final DailyExecutionService service;
    public DailyExecutionController(DailyExecutionService service) { this.service = service; }
    @GetMapping("/{date}")
    @Operation(summary = "Read an execution without mutation")
    @ApiResponse(responseCode = "200", description = "Current execution and summary")
    public ExecutionResponse get(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId,
            @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date) {
        UUID user = UUID.fromString(principal.getSubject());
        return service.get(user, familyId, memberId, JsonFields.date(date));
    }
    @PutMapping("/{date}")
    @Operation(summary = "Materialize or synchronize today; past dates only close existing history")
    @ApiResponse(responseCode = "200", description = "Current execution and summary")
    public ExecutionResponse materialize(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId,
            @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date) {
        UUID user = UUID.fromString(principal.getSubject());
        return service.materialize(user, familyId, memberId, JsonFields.date(date));
    }
    @PostMapping("/{date}/items/{itemId}/complete")
    @Operation(summary = "Complete an item idempotently; open today or explicitly reopened history")
    @ApiResponse(responseCode = "200", description = "Current execution and summary")
    public ExecutionResponse complete(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId,
            @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date, @PathVariable UUID itemId) {
        UUID user = UUID.fromString(principal.getSubject());
        return service.complete(user, familyId, memberId, JsonFields.date(date), itemId, true);
    }
    @PostMapping("/{date}/items/{itemId}/uncomplete")
    @Operation(summary = "Undo completion idempotently; clears actor and timestamp")
    @ApiResponse(responseCode = "200", description = "Current execution and summary")
    public ExecutionResponse uncomplete(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId,
            @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date, @PathVariable UUID itemId) {
        UUID user = UUID.fromString(principal.getSubject());
        return service.complete(user, familyId, memberId, JsonFields.date(date), itemId, false);
    }
    @PostMapping("/{date}/finalize")
    @Operation(summary = "OWNER or ADMIN: freeze snapshots and states, including pending items")
    @ApiResponse(responseCode = "200", description = "Current execution and summary")
    public ExecutionResponse finalizeExecution(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId,
            @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date) {
        UUID user = UUID.fromString(principal.getSubject());
        return service.finalizeExecution(user, familyId, memberId, JsonFields.date(date));
    }
    @PostMapping("/{date}/reopen")
    @Operation(summary = "OWNER or ADMIN: reopen for corrections without resynchronization")
    @ApiResponse(responseCode = "200", description = "Current execution and summary")
    public ExecutionResponse reopen(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId,
            @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date) {
        UUID user = UUID.fromString(principal.getSubject());
        return service.reopen(user, familyId, memberId, JsonFields.date(date));
    }
    @GetMapping
    @Operation(summary = "Page existing history", description = "Required inclusive from/to ISO dates; date DESC, id DESC; page >= 0, size 1..100, defaults 0/20. Never creates missing days.")
    public ExecutionPage history(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId,
            @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam String from,
            @Parameter(schema = @Schema(type = "string", format = "date")) @RequestParam String to,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(defaultValue = "20") int size) {
        return service.history(UUID.fromString(principal.getSubject()), familyId, memberId, JsonFields.date(from), JsonFields.date(to), page, size);
    }
}
