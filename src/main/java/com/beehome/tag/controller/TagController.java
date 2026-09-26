package com.beehome.tag.controller;

import com.beehome.tag.dto.*;
import com.beehome.tag.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/families/{familyId}/tags")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Invalid tag fields or pagination")
@ApiResponse(responseCode = "401", description = "Bearer authentication required")
@ApiResponse(responseCode = "403", description = "Tag changes require OWNER or ADMIN")
@ApiResponse(responseCode = "404", description = "Family or tag inaccessible")
@ApiResponse(responseCode = "409", description = "Equivalent tag name already exists in this family")
public class TagController {
    private final TagService service;
    public TagController(TagService service) { this.service = service; }
    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    @PostMapping @Operation(summary = "Create a family tag")
    @ApiResponse(responseCode = "201", description = "Tag created")
    public ResponseEntity<TagResponse> create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @RequestBody TagRequest body) {
        TagResponse response = service.create(user(jwt), familyId, body);
        return ResponseEntity.created(URI.create("/api/families/" + familyId + "/tags/" + response.id())).body(response);
    }
    @GetMapping @Operation(summary = "List family tags, optionally filtering names")
    public TagPage list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId,
            @RequestParam(required = false) String query, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.list(user(jwt), familyId, query, page, size); }
    @GetMapping("/{tagId}") @Operation(summary = "Read a family tag")
    public TagResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @PathVariable UUID tagId) {
        return service.get(user(jwt), familyId, tagId);
    }
    @PatchMapping("/{tagId}") @Operation(summary = "Rename or recolor a family tag")
    public TagResponse patch(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @PathVariable UUID tagId, @RequestBody TagRequest body) {
        return service.patch(user(jwt), familyId, tagId, body);
    }
    @DeleteMapping("/{tagId}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary = "Delete a tag and its associations")
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @PathVariable UUID tagId) {
        service.delete(user(jwt), familyId, tagId);
    }
}
