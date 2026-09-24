package com.beehome.study.controller;

import com.beehome.study.dto.StudySubjectResponse;
import com.beehome.study.dto.StudySubjectRequest;
import com.beehome.study.service.StudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/families/{familyId}/study-subjects")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Invalid subject request", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "401", description = "Bearer authentication required", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "403", description = "ADMIN or OWNER required for writes", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "Family or subject inaccessible", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "409", description = "Normalized subject name already exists", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class StudySubjectController {
    private final StudyService service;
    public StudySubjectController(StudyService service) { this.service = service; }
    private static UUID user(Jwt principal) { return UUID.fromString(principal.getSubject()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a family study subject (ADMIN or OWNER)")
    public StudySubjectResponse create(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @RequestBody StudySubjectRequest body) {
        return service.createSubject(user(principal), familyId, body);
    }
    @GetMapping @Operation(summary = "List ordered study subjects")
    public List<StudySubjectResponse> list(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.listSubjects(user(principal), familyId, includeInactive);
    }
    @GetMapping("/{subjectId}") @Operation(summary = "Read a study subject")
    public StudySubjectResponse get(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID subjectId) {
        return service.getSubject(user(principal), familyId, subjectId);
    }
    @PatchMapping("/{subjectId}") @Operation(summary = "Edit a study subject (ADMIN or OWNER)")
    public StudySubjectResponse patch(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID subjectId, @RequestBody StudySubjectRequest body) {
        return service.patchSubject(user(principal), familyId, subjectId, body);
    }
    @PostMapping("/{subjectId}/deactivate") @Operation(summary = "Deactivate a study subject")
    public StudySubjectResponse deactivate(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID subjectId) {
        return service.activeSubject(user(principal), familyId, subjectId, false);
    }
    @PostMapping("/{subjectId}/reactivate") @Operation(summary = "Reactivate a study subject")
    public StudySubjectResponse reactivate(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID subjectId) {
        return service.activeSubject(user(principal), familyId, subjectId, true);
    }
}
