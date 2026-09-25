package com.beehome.reading.controller;
import com.beehome.reading.dto.*;
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
@RequestMapping("/api/families/{familyId}/books")
public class BookController {
    private final ReadingService service;
    public BookController(ReadingService service) { this.service=service; }
    private UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }
    @PostMapping @Operation(summary="Create a family book; only title is required")
    @ApiResponse(responseCode="201",description="Book created")
    public ResponseEntity<BookResponse> create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@RequestBody BookRequest body) {
        var result=service.createBook(user(jwt),familyId,body);
        return ResponseEntity.created(URI.create("/api/families/"+familyId+"/books/"+result.id())).body(result);
    }
    @GetMapping @Operation(summary="Page family books, newest first; size 1–100")
    public ReadingPage<BookResponse> list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.listBooks(user(jwt),familyId,page,size);
    }
    @GetMapping("/{bookId}") @Operation(summary="Read a family book")
    public BookResponse get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID bookId) {
        return service.getBook(user(jwt),familyId,bookId);
    }
    @PutMapping("/{bookId}") @Operation(summary="Replace book metadata; omitted optional fields become null")
    public BookResponse replace(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID bookId,@RequestBody BookRequest body) {
        return service.replaceBook(user(jwt),familyId,bookId,body);
    }
    @DeleteMapping("/{bookId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary="Delete an unused book; any reading journey prevents deletion")
    @ApiResponse(responseCode="204",description="Book deleted")
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID familyId,@PathVariable UUID bookId) {
        service.deleteBook(user(jwt),familyId,bookId);
    }
}
