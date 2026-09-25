package com.beehome.reading.controller;
import com.beehome.reading.dto.*;
import com.beehome.reading.entity.ChildBookStatus;
import com.beehome.reading.service.ReadingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.http.*;
import java.net.URI;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController
@SecurityRequirement(name="bearerAuth")
@ApiResponse(responseCode="400",description="Invalid fields, page range, duration, dates, media, or pagination; see docs/books-reading.md")
@ApiResponse(responseCode="401",description="Bearer authentication required")
@ApiResponse(responseCode="403",description="Writes require OWNER or ADMIN")
@ApiResponse(responseCode="404",description="Family, child, book, journey, or session inaccessible")
@ApiResponse(responseCode="409",description="Book is in use or already active")
@RequestMapping("/api/families/{familyId}/children/{childId}/books")
public class ChildBookController {
    private final ReadingService service;
    public ChildBookController(ReadingService service) { this.service=service; }
    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    @PostMapping @Operation(summary="Start a reading journey; default status PLANNED")
    @ApiResponse(responseCode="201",description="Journey created")
    public ResponseEntity<ChildBookDetail> create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID childId,
            @RequestBody ChildBookRequest body) {
        var result=service.createJourney(user(jwt),familyId,childId,body);
        return ResponseEntity.created(URI.create("/api/families/"+familyId+"/children/"+childId+"/books/"+result.id())).body(result);
    }
    @GetMapping @Operation(summary="Page child journeys with optional status; includes derived progress")
    public ReadingPage<ChildBookDetail> list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID childId,
            @RequestParam(required=false) ChildBookStatus status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.listJourneys(user(jwt),familyId,childId,status,page,size);
    }
    @GetMapping("/{childBookId}") @Operation(summary="Read a journey and its derived progress")
    public ChildBookDetail get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID childId,@PathVariable UUID childBookId) {
        return service.getJourney(user(jwt),familyId,childId,childBookId);
    }
    @PatchMapping("/{childBookId}") @Operation(summary="Patch status and dates; completion requires completedOn; bookId is immutable")
    public ChildBookDetail patch(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID childId,@PathVariable UUID childBookId,
            @RequestBody ChildBookRequest body) {
        return service.patchJourney(user(jwt),familyId,childId,childBookId,body);
    }
}
