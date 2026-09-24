package com.beehome.photorecord.controller;

import com.beehome.photorecord.dto.*;
import com.beehome.photorecord.service.PhotoRecordService;
import com.beehome.shared.exception.InputException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/families/{familyId}/children/{childId}/photo-records")
@SecurityRequirement(name="bearerAuth")
@ApiResponse(responseCode="401",description="Bearer token required")
@ApiResponse(responseCode="404",description="Family, child, or photo record inaccessible")
public class PhotoRecordController {
    private final PhotoRecordService service;
    public PhotoRecordController(PhotoRecordService service) { this.service=service; }
    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    private LocalDate date(String value) {
        if (value==null || value.isBlank()) return null;
        try { return LocalDate.parse(value); } catch (DateTimeParseException e) { throw new InputException(); }
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Create dated child photo record with ordered images")
    @ApiResponse(responseCode="400",description="Invalid fields or media")
    @ApiResponse(responseCode="201",description="Photo record created")
    public PhotoRecordResponse create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,
            @PathVariable UUID childId,@RequestBody PhotoRecordRequest body) {
        return service.create(user(jwt),familyId,childId,body);
    }
    @GetMapping
    @Operation(summary="Page child photo records by optional inclusive dates")
    public PhotoRecordPage list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,
            @PathVariable UUID childId,@RequestParam(required=false) String from,@RequestParam(required=false) String to,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(user(jwt),familyId,childId,date(from),date(to),page,size);
    }
    @GetMapping("/{photoRecordId}") @Operation(summary="Read child photo record")
    public PhotoRecordResponse get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,
            @PathVariable UUID childId,@PathVariable UUID photoRecordId) {
        return service.get(user(jwt),familyId,childId,photoRecordId);
    }
    @PutMapping("/{photoRecordId}") @Operation(summary="Replace date, description, and ordered images")
    public PhotoRecordResponse replace(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,
            @PathVariable UUID childId,@PathVariable UUID photoRecordId,@RequestBody PhotoRecordRequest body) {
        return service.replace(user(jwt),familyId,childId,photoRecordId,body);
    }
    @DeleteMapping("/{photoRecordId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Delete record and links while retaining media")
    @ApiResponse(responseCode="204",description="Photo record deleted")
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,
            @PathVariable UUID childId,@PathVariable UUID photoRecordId) {
        service.delete(user(jwt),familyId,childId,photoRecordId);
    }
}
