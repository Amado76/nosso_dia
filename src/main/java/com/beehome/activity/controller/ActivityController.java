package com.beehome.activity.controller;

import com.beehome.activity.dto.*;
import com.beehome.activity.service.ActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/families/{familyId}/extracurricular-activities")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Invalid activity input")
@ApiResponse(responseCode = "401", description = "Bearer authentication required")
@ApiResponse(responseCode = "403", description = "Catalog write requires ADMIN or OWNER")
@ApiResponse(responseCode = "404", description = "Family or activity inaccessible")
@ApiResponse(responseCode = "409", description = "Normalized activity name already exists")
public class ActivityController {
    private final ActivityService service;
    public ActivityController(ActivityService service) { this.service = service; }
    private static UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Operation(summary = "Create a family extracurricular activity")
    public ActivityResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @RequestBody ActivityRequest body) {
        return service.createActivity(user(jwt), familyId, body);
    }
    @GetMapping @Operation(summary = "List extracurricular activities in display order")
    public List<ActivityResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId,
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return service.listActivities(user(jwt), familyId, includeInactive);
    }
    @GetMapping("/{activityId}") @Operation(summary = "Read an extracurricular activity")
    public ActivityResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @PathVariable UUID activityId) {
        return service.getActivity(user(jwt), familyId, activityId);
    }
    @PatchMapping("/{activityId}") @Operation(summary = "Edit an extracurricular activity")
    public ActivityResponse patch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @PathVariable UUID activityId,
            @RequestBody ActivityRequest body) { return service.patchActivity(user(jwt), familyId, activityId, body); }
    @PostMapping("/{activityId}/deactivate") @Operation(summary = "Deactivate an extracurricular activity")
    public ActivityResponse deactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @PathVariable UUID activityId) {
        return service.activeActivity(user(jwt), familyId, activityId, false);
    }
    @PostMapping("/{activityId}/reactivate") @Operation(summary = "Reactivate an extracurricular activity")
    public ActivityResponse reactivate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @PathVariable UUID activityId) {
        return service.activeActivity(user(jwt), familyId, activityId, true);
    }
}
