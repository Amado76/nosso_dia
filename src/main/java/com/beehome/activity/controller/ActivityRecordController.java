package com.beehome.activity.controller;

import com.beehome.activity.dto.*;
import com.beehome.activity.service.ActivityService;
import com.beehome.shared.dto.JsonFields;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/families/{familyId}/children/{childId}/extracurricular-records")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Invalid record input or period")
@ApiResponse(responseCode = "401", description = "Bearer authentication required")
@ApiResponse(responseCode = "404", description = "Family, child, activity, record, or tag inaccessible")
public class ActivityRecordController {
    private final ActivityService service;
    public ActivityRecordController(ActivityService service) { this.service = service; }
    private static UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "Record an extracurricular occurrence")
    public ActivityRecordResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId,
            @PathVariable UUID childId, @RequestBody ActivityRecordRequest body) {
        return service.createRecord(user(jwt), familyId, childId, body);
    }
    @GetMapping("/{recordId}") @Operation(summary = "Read one extracurricular occurrence")
    public ActivityRecordResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId,
            @PathVariable UUID childId, @PathVariable UUID recordId) {
        return service.getRecord(user(jwt), familyId, childId, recordId);
    }
    @PatchMapping("/{recordId}") @Operation(summary = "Correct an extracurricular occurrence")
    public ActivityRecordResponse patch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId,
            @PathVariable UUID childId, @PathVariable UUID recordId, @RequestBody ActivityRecordRequest body) {
        return service.patchRecord(user(jwt), familyId, childId, recordId, body);
    }
    @DeleteMapping("/{recordId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "Delete an extracurricular occurrence")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @PathVariable UUID childId,
            @PathVariable UUID recordId) { service.deleteRecord(user(jwt), familyId, childId, recordId); }
    @GetMapping @Operation(summary = "Page extracurricular occurrences in an inclusive period")
    public ActivityRecordPage list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId,
            @PathVariable UUID childId, @RequestParam String from, @RequestParam String to,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listRecords(user(jwt), familyId, childId, JsonFields.date(from), JsonFields.date(to), page, size);
    }
}
