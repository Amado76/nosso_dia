package com.beehome.study.controller;

import com.beehome.shared.dto.JsonFields;
import com.beehome.study.dto.*;
import com.beehome.study.service.StudyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/families/{familyId}/members/{memberId}")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Invalid session request, date range, or pagination", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "401", description = "Bearer authentication required", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "403", description = "ADMIN or OWNER required for correction and void", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "Family, member, subject, session, or execution item inaccessible", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "409", description = "Invalid timer state or concurrent change; reload", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
public class StudySessionController {
    private final StudyService service;
    public StudySessionController(StudyService service) { this.service = service; }
    private static UUID user(Jwt principal) { return UUID.fromString(principal.getSubject()); }
    @PostMapping("/study-sessions/start") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a server timed study session")
    public StudySessionResponse start(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @RequestBody StudySessionRequest body) {
        return service.start(user(principal), familyId, memberId, body);
    }
    @PostMapping("/study-sessions") @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record a completed manual study session")
    public StudySessionResponse manual(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @RequestBody StudySessionRequest body) {
        return service.manual(user(principal), familyId, memberId, body);
    }
    @GetMapping("/study-sessions/current") @Operation(summary = "Read running study session, or 204 when none")
    public ResponseEntity<StudySessionResponse> current(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId) {
        var result = service.current(user(principal), familyId, memberId);
        return result == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(result);
    }
    @GetMapping("/study-sessions/{sessionId}") @Operation(summary = "Read a non-voided study session")
    public StudySessionResponse get(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @PathVariable UUID sessionId) {
        return service.get(user(principal), familyId, memberId, sessionId);
    }
    @PostMapping("/study-sessions/{sessionId}/pause") @Operation(summary = "Pause and accumulate running time")
    public StudySessionResponse pause(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @PathVariable UUID sessionId) {
        return service.command(user(principal), familyId, memberId, sessionId, "pause");
    }
    @PostMapping("/study-sessions/{sessionId}/resume") @Operation(summary = "Resume a paused timer")
    public StudySessionResponse resume(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @PathVariable UUID sessionId) {
        return service.command(user(principal), familyId, memberId, sessionId, "resume");
    }
    @PostMapping("/study-sessions/{sessionId}/finish") @Operation(summary = "Finish a running or paused timer")
    public StudySessionResponse finish(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @PathVariable UUID sessionId) {
        return service.command(user(principal), familyId, memberId, sessionId, "finish");
    }
    @PatchMapping("/study-sessions/{sessionId}") @Operation(summary = "Correct completed session content (ADMIN or OWNER)")
    public StudySessionResponse correct(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @PathVariable UUID sessionId, @RequestBody StudySessionRequest body) {
        return service.correct(user(principal), familyId, memberId, sessionId, body);
    }
    @PostMapping("/study-sessions/{sessionId}/void") @Operation(summary = "Void a session without deleting it (ADMIN or OWNER)")
    public StudySessionResponse voidSession(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @PathVariable UUID sessionId) {
        return service.voidSession(user(principal), familyId, memberId, sessionId);
    }
    @GetMapping("/study-sessions") @Operation(summary = "Page non-voided study history by inclusive dates", description="Repeated tagIds require all requested family tags and combine with date and subject filters")
    public StudySessionPage history(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @RequestParam String from, @RequestParam String to,
            @RequestParam(required = false) UUID subjectId, @RequestParam(required = false) List<UUID> tagIds,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.history(user(principal), familyId, memberId, JsonFields.date(from), JsonFields.date(to), subjectId,
                tagIds==null ? List.of() : tagIds, page, size);
    }
    @GetMapping("/study-summary") @Operation(summary = "Completed study duration by subject and inclusive date range")
    public StudySummary summary(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId,
            @PathVariable UUID memberId, @RequestParam String from, @RequestParam String to) {
        return service.summary(user(principal), familyId, memberId, JsonFields.date(from), JsonFields.date(to));
    }
}
