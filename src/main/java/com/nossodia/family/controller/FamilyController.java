package com.nossodia.family.controller;

import com.nossodia.family.entity.Family;
import com.nossodia.family.service.FamilyService;

import com.nossodia.family.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/families")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Missing, invalid, or unavailable user identity", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "400", description = "Invalid input", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
public class FamilyController {
    private final FamilyService families;

    public FamilyController(FamilyService families) { this.families = families; }

    @PostMapping
    @Operation(summary = "Create a family and become its OWNER")
    @ApiResponse(responseCode = "201", description = "Family and OWNER membership created",
            headers = @Header(name = "Location", description = "Relative URI of the created family", schema = @Schema(type = "string")),
            content = @Content(schema = @Schema(implementation = FamilyResponse.class)))
    public ResponseEntity<FamilyResponse> create(@AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody CreateFamilyRequest request) {
        var family = families.create(UUID.fromString(principal.getSubject()), request.name(), request.timezone());
        return ResponseEntity.created(URI.create("/api/families/" + family.id())).body(family);
    }

    @GetMapping
    @Operation(summary = "List the caller's families ordered by creation time and ID")
    @ApiResponse(responseCode = "200", description = "Accessible family slice", content = @Content(schema = @Schema(implementation = FamilyPage.class)))
    public FamilyPage list(@AuthenticationPrincipal Jwt principal,
            @Parameter(description = "Zero-based page; page * size must be below 2147483647")
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "{validation.family.pagination}") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "{validation.family.pagination}")
            @Max(value = 100, message = "{validation.family.pagination}") int size) {
        return families.list(UUID.fromString(principal.getSubject()), page, size);
    }

    @GetMapping("/{familyId}")
    @Operation(summary = "Read a family accessible to the caller")
    @ApiResponse(responseCode = "200", description = "Family details", content = @Content(schema = @Schema(implementation = FamilyResponse.class)))
    @ApiResponse(responseCode = "404", description = "Family missing or inaccessible", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public FamilyResponse get(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId) {
        return families.get(UUID.fromString(principal.getSubject()), familyId);
    }

    @PatchMapping("/{familyId}")
    @Operation(summary = "Edit a family as OWNER or ADMIN", description = "Name and timezone are optional; omission preserves, null is invalid. Empty patches are invalid. Last committed edit wins.")
    @ApiResponse(responseCode = "200", description = "Updated family details", content = @Content(schema = @Schema(implementation = FamilyResponse.class)))
    @ApiResponse(responseCode = "403", description = "Membership does not allow renaming", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Family missing or inaccessible", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public FamilyResponse rename(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @Valid @RequestBody PatchFamilyRequest request) {
        return families.edit(UUID.fromString(principal.getSubject()), familyId, request);
    }
}
