package com.beehome.reading.controller;
import com.beehome.reading.dto.*;
import com.beehome.reading.service.ReadingService;
import com.beehome.shared.dto.JsonFields;
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
@RequestMapping("/api/families/{familyId}/children/{childId}")
public class ReadingSessionController {
    private final ReadingService service;
    public ReadingSessionController(ReadingService service) { this.service=service; }
    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    @PostMapping("/reading-sessions") @Operation(summary="Record reading; date and childBookId required, measurements optional")
    @ApiResponse(responseCode="201",description="Session created")
    public ResponseEntity<ReadingSessionDetail> create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID childId,
            @RequestBody ReadingSessionRequest body) {
        var result=service.createSession(user(jwt),familyId,childId,body);
        return ResponseEntity.created(URI.create("/api/families/"+familyId+"/children/"+childId+"/reading-sessions/"+result.id())).body(result);
    }
    @GetMapping("/reading-sessions") @Operation(summary="Page reading by optional inclusive dates and bookId; date/createdAt/id descending, size 1–100")
    public ReadingPage<ReadingSessionDetail> list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID childId,
            @RequestParam(required=false) String from,@RequestParam(required=false) String to,@RequestParam(required=false) UUID bookId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.listSessions(user(jwt),familyId,childId,JsonFields.date(from),JsonFields.date(to),bookId,page,size);
    }
    @GetMapping("/reading-sessions/{sessionId}") @Operation(summary="Read a reading session")
    public ReadingSessionDetail get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID childId,@PathVariable UUID sessionId) {
        return service.getSession(user(jwt),familyId,childId,sessionId);
    }
    @PutMapping("/reading-sessions/{sessionId}") @Operation(summary="Replace session details; omit childBookId, which is immutable")
    public ReadingSessionDetail replace(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID childId,@PathVariable UUID sessionId,
            @RequestBody ReadingSessionRequest body) {
        return service.replaceSession(user(jwt),familyId,childId,sessionId,body);
    }
    @DeleteMapping("/reading-sessions/{sessionId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Remove a session; derived progress and metrics reflect deletion")
    @ApiResponse(responseCode="204",description="Session deleted")
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID childId,@PathVariable UUID sessionId) {
        service.deleteSession(user(jwt),familyId,childId,sessionId);
    }
    @GetMapping("/reading-summary") @Operation(summary="Reading totals for inclusive dates (configured report limit, default 366 days); books counts distinct books with sessions")
    public ReadingSummary summary(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID childId,
            @RequestParam String from,@RequestParam String to) {
        return service.summary(user(jwt),familyId,childId,JsonFields.date(from),JsonFields.date(to));
    }
}
