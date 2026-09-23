package com.nossodia.dailyplan.controller;

import com.nossodia.dailyplan.dto.*;
import com.nossodia.dailyplan.service.DailyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.nossodia.shared.dto.ItemOrderRequest;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import com.nossodia.shared.dto.JsonFields;

@RestController
@RequestMapping("/api/families/{familyId}/members/{memberId}/daily-plan")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Invalid fields, date, order, or collection limit", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "401", description = "Bearer authentication required", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "403", description = "OWNER or ADMIN required for writes", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "Resource missing, inactive member, or inaccessible nested resource", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class DailyPlanController {
    private final DailyPlanService service;
    public DailyPlanController(DailyPlanService service) { this.service = service; }
    @GetMapping
    @Operation(summary = "Resolve active planning for an explicit family-local date", description = "Required ISO date; no rows are created; maximum 1000 resolved items")
    public ResolvedDailyPlan get(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId,
            @Parameter(schema = @Schema(type = "string", format = "date", pattern = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")) @RequestParam String date) {
        return service.resolve(user(principal), familyId, memberId, JsonFields.date(date));
    }
    @PutMapping("/{date}")
    @Operation(summary = "Set or clear the daily note", description = "Required note field; null or blank clears; creates a row only for nonempty content")
    public ResolvedDailyPlan note(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId,
            @Parameter(schema = @Schema(type = "string", format = "date", pattern = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")) @PathVariable String date, @RequestBody DailyNoteRequest request) {
        return service.note(user(principal), familyId, memberId, JsonFields.date(date), request);
    }
    @GetMapping("/{date}/items")
    @Operation(summary = "List daily items including inactive items", description = "Any family membership may read; active same-family person required. Ordered by sortOrder then ID; maximum 500 items, no pagination. Returns an empty array when no plan exists; creates no rows.")
    public List<DailyPlanItemResponse> listItems(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId,
            @Parameter(schema = @Schema(type = "string", format = "date", pattern = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")) @PathVariable String date) {
        return service.listItems(user(principal), familyId, memberId, JsonFields.date(date));
    }
    @PostMapping("/{date}/items")
    @Operation(summary = "Create a planning item", description = "Explicit non-negative sortOrder; HH:mm family-local time; maximum 500 items per parent")
    @ApiResponse(responseCode = "201", description = "Created; Location identifies the item for subsequent mutations", headers = @Header(name = "Location", schema = @Schema(type = "string")))
    public ResponseEntity<DailyPlanItemResponse> createItem(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @Parameter(schema = @Schema(type = "string", format = "date", pattern = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")) @PathVariable String date, @RequestBody CreateDailyPlanItemRequest request) {
        var result = service.createItem(user(principal), familyId, memberId, JsonFields.date(date), request);
        return ResponseEntity.created(URI.create("/api/families/" + familyId + "/members/" + memberId + "/daily-plan/" + date + "/items/" + result.id())).body(result);
    }
    @PatchMapping("/{date}/items/{itemId}")
    @Operation(summary = "Partially edit an item", description = "Omission preserves; null clears description/time; empty PATCH is invalid")
    public DailyPlanItemResponse editItem(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @Parameter(schema = @Schema(type = "string", format = "date", pattern = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")) @PathVariable String date, @PathVariable UUID itemId, @RequestBody PatchDailyPlanItemRequest request) {
        return service.editItem(user(principal), familyId, memberId, JsonFields.date(date), itemId, request);
    }
    @PostMapping("/{date}/items/{itemId}/deactivate")
    @Operation(summary = "Deactivate an item idempotently")
    public DailyPlanItemResponse deactivateItem(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @Parameter(schema = @Schema(type = "string", format = "date", pattern = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")) @PathVariable String date, @PathVariable UUID itemId) {
        return service.setItemActive(user(principal), familyId, memberId, JsonFields.date(date), itemId, false);
    }
    @PostMapping("/{date}/items/{itemId}/reactivate")
    @Operation(summary = "Reactivate an item idempotently")
    public DailyPlanItemResponse reactivateItem(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @Parameter(schema = @Schema(type = "string", format = "date", pattern = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")) @PathVariable String date, @PathVariable UUID itemId) {
        return service.setItemActive(user(principal), familyId, memberId, JsonFields.date(date), itemId, true);
    }
    @PutMapping("/{date}/items/order")
    @Operation(summary = "Replace the complete item order, including inactive items", description = "IDs and sortOrder values must be unique; empty list is valid only for an empty parent")
    public List<DailyPlanItemResponse> reorder(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @Parameter(schema = @Schema(type = "string", format = "date", pattern = "^[0-9]{4}-[0-9]{2}-[0-9]{2}$")) @PathVariable String date, @RequestBody ItemOrderRequest request) {
        return service.reorder(user(principal), familyId, memberId, JsonFields.date(date), request);
    }
    private static UUID user(Jwt principal) { return UUID.fromString(principal.getSubject()); }
}
