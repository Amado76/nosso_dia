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
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary="Page child photo records by date, description, and all requested family tags")
    public PhotoRecordPage list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,
            @PathVariable UUID childId,@Parameter(description="Exact event date; cannot accompany from or to") @RequestParam(required=false) String date,
            @Parameter(description="Inclusive lower event date") @RequestParam(required=false) String from,
            @Parameter(description="Inclusive upper event date") @RequestParam(required=false) String to,
            @Parameter(description="Case-insensitive description substring, maximum 120 characters") @RequestParam(required=false) String query,
            @Parameter(description="Repeated family tag IDs; every tag must match") @RequestParam(required=false) List<UUID> tagIds,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.list(user(jwt),familyId,childId,date(date),date(from),date(to),query,tagIds,page,size);
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
    @PatchMapping("/{photoRecordId}") @Operation(summary="Update photo record date, description, or family tags")
    public PhotoRecordResponse patch(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,
            @PathVariable UUID childId,@PathVariable UUID photoRecordId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description="Any nonempty subset of date, description, and tagIds; omitted fields are preserved",
                    content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=PhotoRecordPatch.class)))
            @RequestBody Map<String,Object> body) {
        return service.patch(user(jwt),familyId,childId,photoRecordId,PhotoRecordPatch.from(body));
    }
    @PostMapping("/{photoRecordId}/media") @Operation(summary="Insert an image at a zero-based position, or append")
    public PhotoRecordResponse addMedia(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,
            @PathVariable UUID childId,@PathVariable UUID photoRecordId,@RequestBody PhotoRecordMediaChange body) {
        return service.addMedia(user(jwt),familyId,childId,photoRecordId,body);
    }
    @PutMapping("/{photoRecordId}/media") @Operation(summary="Replace ordered image IDs without changing metadata or tags")
    public PhotoRecordResponse setMedia(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,
            @PathVariable UUID childId,@PathVariable UUID photoRecordId,@RequestBody PhotoRecordMediaOrder body) {
        if (body==null) throw new InputException();
        return service.setMedia(user(jwt),familyId,childId,photoRecordId,body.mediaIds());
    }
    @PutMapping("/{photoRecordId}/media/{mediaId}") @Operation(summary="Replace one image at its current position")
    public PhotoRecordResponse replaceMedia(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,
            @PathVariable UUID childId,@PathVariable UUID photoRecordId,@PathVariable UUID mediaId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content=@io.swagger.v3.oas.annotations.media.Content(schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=PhotoRecordMediaReplacement.class)))
            @RequestBody Map<String,Object> body) {
        return service.replaceMedia(user(jwt),familyId,childId,photoRecordId,mediaId,PhotoRecordMediaReplacement.from(body));
    }
    @DeleteMapping("/{photoRecordId}/media/{mediaId}") @Operation(summary="Remove one image while retaining the media asset")
    public PhotoRecordResponse removeMedia(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,
            @PathVariable UUID childId,@PathVariable UUID photoRecordId,@PathVariable UUID mediaId) {
        return service.removeMedia(user(jwt),familyId,childId,photoRecordId,mediaId);
    }
    @DeleteMapping("/{photoRecordId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Delete record and links while retaining media")
    @ApiResponse(responseCode="204",description="Photo record deleted")
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,
            @PathVariable UUID childId,@PathVariable UUID photoRecordId) {
        service.delete(user(jwt),familyId,childId,photoRecordId);
    }
}
