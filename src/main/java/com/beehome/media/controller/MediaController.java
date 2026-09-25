package com.beehome.media.controller;

import com.beehome.media.dto.*;
import com.beehome.media.service.MediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/families/{familyId}/media")
@SecurityRequirement(name="bearerAuth")
@ApiResponse(responseCode="401", description="Bearer token required")
@ApiResponse(responseCode="404", description="Family or media inaccessible")
public class MediaController {
    private final MediaService service;
    public MediaController(MediaService service) { this.service=service; }
    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary="Upload a private JPEG, PNG, or WebP image",
            description="Maximum 8192 pixels per dimension, 16777216 total pixels across frames, and 100 frames. " +
                    "WebP canvas dimensions are also bounded. Byte limits are configured by the server. " +
                    "Exceeding any limit, including multipart parsing limits, returns 400 MEDIA_TOO_LARGE.")
    @ApiResponse(responseCode="201", description="Media metadata", content=@Content(schema=@Schema(implementation=MediaResponse.class)))
    @ApiResponse(responseCode="400", description="Invalid or oversized image")
    @ApiResponse(responseCode="500", description="Private storage failure")
    public MediaResponse upload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId,
            @RequestPart("file") MultipartFile file) { return service.upload(user(jwt),familyId,file); }
    @GetMapping
    @Operation(summary="Page family media metadata; optionally only unattached media")
    public MediaPage list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId,
            @RequestParam(defaultValue="false") boolean unattached, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return service.list(user(jwt),familyId,unattached,page,size); }
    @GetMapping("/{mediaId}/content")
    @Operation(summary="Read authorized private image content")
    @ApiResponse(responseCode="200", description="Image bytes", content={@Content(mediaType="image/jpeg"),@Content(mediaType="image/png"),@Content(mediaType="image/webp")})
    public ResponseEntity<org.springframework.core.io.Resource> content(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID familyId, @PathVariable UUID mediaId) {
        var content=service.content(user(jwt),familyId,mediaId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline")
                .header(HttpHeaders.CACHE_CONTROL,"private, no-store")
                .body(content.resource());
    }
    @DeleteMapping("/{mediaId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Delete unattached media and its private content")
    @ApiResponse(responseCode="204",description="Media deleted")
    @ApiResponse(responseCode="409",description="Media is used by a photo record or book cover")
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID mediaId) {
        service.delete(user(jwt),familyId,mediaId);
    }
}
