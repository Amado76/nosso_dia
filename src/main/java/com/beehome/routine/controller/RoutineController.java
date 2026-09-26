package com.beehome.routine.controller;

import com.beehome.routine.dto.*;
import com.beehome.routine.service.RoutineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ProblemDetail;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.beehome.shared.dto.ItemOrderRequest;
import java.net.URI;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/families/{familyId}/routines")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Invalid fields, date, order, or collection limit", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "401", description = "Bearer authentication required", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "403", description = "OWNER or ADMIN required for writes", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "Resource missing, inactive member, or inaccessible nested resource", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class RoutineController {
    private final RoutineService service;
    public RoutineController(RoutineService service) { this.service = service; }
    @PostMapping
    @Operation(summary = "Create a routine with one or more weekdays")
    @ApiResponse(responseCode = "201", description = "Created; Location identifies the routine", headers = @Header(name = "Location", schema = @Schema(type = "string")))
    public ResponseEntity<RoutineResponse> create(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @RequestBody CreateRoutineRequest request) {
        var result = service.create(user(principal), familyId, request);
        return ResponseEntity.created(URI.create("/api/families/" + familyId + "/routines/" + result.id())).body(result);
    }
    @GetMapping
    @Operation(summary = "List routines", description = "Zero-based page, default size 20, maximum 100; ordered by createdAt then ID")
    public RoutinePage list(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @RequestParam(defaultValue = "false") String includeInactive, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(user(principal), familyId, includeInactive, page, size);
    }
    @GetMapping("/{routineId}")
    @Operation(summary = "Read routine details including inactive items")
    public RoutineResponse get(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID routineId) {
        return service.get(user(principal), familyId, routineId);
    }
    @PatchMapping("/{routineId}")
    @Operation(summary = "Partially edit routine configuration")
    public RoutineResponse edit(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID routineId,
            @RequestBody PatchRoutineRequest request) {
        return service.edit(user(principal), familyId, routineId, request);
    }
    @PostMapping("/{routineId}/deactivate")
    @Operation(summary = "Deactivate a routine idempotently")
    public RoutineResponse deactivate(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID routineId) {
        return service.setActive(user(principal), familyId, routineId, false);
    }
    @PostMapping("/{routineId}/reactivate")
    @Operation(summary = "Reactivate a routine idempotently")
    public RoutineResponse reactivate(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID routineId) {
        return service.setActive(user(principal), familyId, routineId, true);
    }
    @PostMapping("/{routineId}/items")
    @Operation(summary = "Create a planning item", description = "Explicit non-negative sortOrder; HH:mm family-local time; maximum 500 items per parent")
    @ApiResponse(responseCode = "201", description = "Created; Location identifies the item for subsequent mutations", headers = @Header(name = "Location", schema = @Schema(type = "string")))
    public ResponseEntity<RoutineItemResponse> createItem(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID routineId, @RequestBody CreateRoutineItemRequest request) {
        var result = service.createItem(user(principal), familyId, routineId, request);
        return ResponseEntity.created(URI.create("/api/families/" + familyId + "/routines/" + routineId + "/items/" + result.id())).body(result);
    }
    @PatchMapping("/{routineId}/items/{itemId}")
    @Operation(summary = "Partially edit an item", description = "Omission preserves; null clears description/time; empty PATCH is invalid")
    public RoutineItemResponse editItem(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID routineId, @PathVariable UUID itemId, @RequestBody PatchRoutineItemRequest request) {
        return service.editItem(user(principal), familyId, routineId, itemId, request);
    }
    @PostMapping("/{routineId}/items/{itemId}/deactivate")
    @Operation(summary = "Deactivate an item idempotently")
    public RoutineItemResponse deactivateItem(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID routineId, @PathVariable UUID itemId) {
        return service.setItemActive(user(principal), familyId, routineId, itemId, false);
    }
    @PostMapping("/{routineId}/items/{itemId}/reactivate")
    @Operation(summary = "Reactivate an item idempotently")
    public RoutineItemResponse reactivateItem(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID routineId, @PathVariable UUID itemId) {
        return service.setItemActive(user(principal), familyId, routineId, itemId, true);
    }
    @PutMapping("/{routineId}/items/order")
    @Operation(summary = "Replace the complete item order, including inactive items", description = "IDs and sortOrder values must be unique; empty list is valid only for an empty parent")
    public RoutineResponse reorder(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID routineId, @RequestBody ItemOrderRequest request) {
        return service.reorder(user(principal), familyId, routineId, request);
    }
    private static UUID user(Jwt principal) { return UUID.fromString(principal.getSubject()); }
}
