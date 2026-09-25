package com.beehome.familymember.controller;

import com.beehome.familymember.dto.*;
import com.beehome.familymember.service.FamilyMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/families/{familyId}/members")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "UNAUTHENTICATED", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "400", description = "Invalid input; VALIDATION_ERROR or framework error", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "403", description = "FORBIDDEN: insufficient persisted family role", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "FAMILY_NOT_FOUND or FAMILY_MEMBER_NOT_FOUND; inaccessible resources are indistinguishable from missing ones", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
public class FamilyMemberController {
    private final FamilyMemberService members;

    public FamilyMemberController(FamilyMemberService members) { this.members = members; }

    @PostMapping
    @Operation(summary = "Create a person as OWNER or ADMIN")
    @ApiResponse(responseCode = "201", description = "Active person created; no account or membership created",
            headers = @Header(name = "Location", description = "Relative member URI", schema = @Schema(type = "string")),
            content = @Content(schema = @Schema(implementation = FamilyMemberResponse.class)))
    public ResponseEntity<FamilyMemberResponse> create(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @Valid @RequestBody CreateFamilyMemberRequest request) {
        var member = members.create(UUID.fromString(principal.getSubject()), familyId, request);
        return ResponseEntity.created(URI.create("/api/families/" + familyId + "/members/" + member.id())).body(member);
    }

    @GetMapping
    @Operation(summary = "List people as any family member", description = "Active by default, ordered by createdAt ASC and id ASC. Zero-based pages; default size 20, maximum 100.")
    @ApiResponse(responseCode = "200", description = "Matching family people", content = @Content(schema = @Schema(implementation = FamilyMemberPage.class)))
    public FamilyMemberPage list(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @Parameter(description = "Only true or false; omitted means false", schema = @Schema(allowableValues = {"true", "false"}, defaultValue = "false"))
            @RequestParam(required = false) String includeInactive,
            @Parameter(description = "Optional explicit classification", schema = @Schema(allowableValues = {"ADULT", "CHILD"}))
            @RequestParam(required = false) String type,
            @Parameter(description = "Zero-based page; page * size must be below 2147483647", schema = @Schema(minimum = "0", defaultValue = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Maximum items per page", schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(defaultValue = "20") int size) {
        return members.list(UUID.fromString(principal.getSubject()), familyId, includeInactive, type, page, size);
    }

    @GetMapping("/{memberId}")
    @Operation(summary = "Read a person, including inactive people, as any family member")
    @ApiResponse(responseCode = "200", description = "Family person", content = @Content(schema = @Schema(implementation = FamilyMemberResponse.class)))
    public FamilyMemberResponse get(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId) {
        return members.get(UUID.fromString(principal.getSubject()), familyId, memberId);
    }

    @PatchMapping("/{memberId}")
    @Operation(summary = "Edit a person as OWNER or ADMIN", description = "Only name, memberType, birthDate, color and preferences. Preferences merge by key; null removes a known preference. Omitted fields remain unchanged; null clears date/color. Last committed update wins for the same field/key.")
    @ApiResponse(responseCode = "200", description = "Family person", content = @Content(schema = @Schema(implementation = FamilyMemberResponse.class)))
    public FamilyMemberResponse edit(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId,
            @Valid @RequestBody PatchFamilyMemberRequest request) {
        return members.edit(UUID.fromString(principal.getSubject()), familyId, memberId, request);
    }

    @PostMapping("/{memberId}/deactivate")
    @Operation(summary = "Deactivate a person as OWNER or ADMIN", description = "Idempotent; preserves the person and any account link.")
    @ApiResponse(responseCode = "200", description = "Family person", content = @Content(schema = @Schema(implementation = FamilyMemberResponse.class)))
    public FamilyMemberResponse deactivate(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId) {
        return members.setActive(UUID.fromString(principal.getSubject()), familyId, memberId, false);
    }

    @PostMapping("/{memberId}/reactivate")
    @Operation(summary = "Reactivate a person as OWNER or ADMIN", description = "Idempotent; returns active=true.")
    @ApiResponse(responseCode = "200", description = "Family person", content = @Content(schema = @Schema(implementation = FamilyMemberResponse.class)))
    public FamilyMemberResponse reactivate(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId) {
        return members.setActive(UUID.fromString(principal.getSubject()), familyId, memberId, true);
    }

    @PutMapping("/{memberId}/link-me")
    @Operation(summary = "Link the caller's account as any family member", description = "No body. Requires existing membership. Same account is idempotent; inactive people cannot receive new links.")
    @ApiResponse(responseCode = "409", description = "FAMILY_MEMBER_ALREADY_LINKED, USER_ALREADY_LINKED_TO_FAMILY_MEMBER, or FAMILY_MEMBER_INACTIVE", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "200", description = "Family person", content = @Content(schema = @Schema(implementation = FamilyMemberResponse.class)))
    public FamilyMemberResponse linkMe(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId) {
        return members.linkMe(UUID.fromString(principal.getSubject()), familyId, memberId);
    }

    @DeleteMapping("/{memberId}/link")
    @Operation(summary = "Unlink own account, or any account as OWNER/ADMIN", description = "Works for inactive people. Empty link: OWNER/ADMIN receive 204, MEMBER receives 403. Does not change membership or tokens.")
    @ApiResponse(responseCode = "204", description = "Account association cleared", content = @Content)
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId) {
        members.unlink(UUID.fromString(principal.getSubject()), familyId, memberId);
        return ResponseEntity.noContent().build();
    }
}
