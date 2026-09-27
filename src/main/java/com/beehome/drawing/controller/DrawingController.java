package com.beehome.drawing.controller;

import com.beehome.drawing.dto.DrawingResponse;
import com.beehome.drawing.dto.DrawingUpdate;
import com.beehome.drawing.exception.DrawingException;
import com.beehome.drawing.service.DrawingService;
import jakarta.servlet.http.HttpServletRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ProblemDetail;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/families/{familyId}/members/{memberId}/drawings/profile-scratchpad")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "UNAUTHENTICATED", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "403", description = "FORBIDDEN: read-only family member attempted a write", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "404", description = "FAMILY_NOT_FOUND or FAMILY_MEMBER_NOT_FOUND", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "400", description = "Invalid document or unsupported format", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
public class DrawingController {
    private final DrawingService drawings;
    private final int maxDocumentBytes;

    public DrawingController(DrawingService drawings,
            @Value("${app.drawings.max-document-bytes:5242880}") int maxDocumentBytes) {
        this.drawings = drawings;
        if (maxDocumentBytes <= 0 || maxDocumentBytes == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("app.drawings.max-document-bytes must be between 1 and 2147483646");
        }
        this.maxDocumentBytes = maxDocumentBytes;
    }

    @GetMapping
    @Operation(summary = "Read a profile scratchpad", description = "Any family member may read. An unsaved scratchpad is virtual and has revision 0.")
    @ApiResponse(responseCode = "200", description = "Current drawing", content = @Content(schema = @Schema(implementation = DrawingResponse.class)))
    public DrawingResponse get(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId) {
        return drawings.get(UUID.fromString(principal.getSubject()), familyId, memberId);
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace a profile scratchpad", description = "OWNER or ADMIN. Full document replacement with expected revision; clear by sending an empty strokes list.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = DrawingUpdate.class)))
    @ApiResponse(responseCode = "200", description = "Saved drawing and new revision", content = @Content(schema = @Schema(implementation = DrawingResponse.class)))
    @ApiResponse(responseCode = "409", description = "DRAWING_VERSION_CONFLICT", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "413", description = "DRAWING_TOO_LARGE", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    public DrawingResponse replace(@AuthenticationPrincipal Jwt principal, @PathVariable UUID familyId, @PathVariable UUID memberId,
            HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > maxDocumentBytes) throw DrawingException.tooLarge();
        byte[] body = request.getInputStream().readNBytes(maxDocumentBytes + 1);
        if (body.length > maxDocumentBytes) throw DrawingException.tooLarge();
        return drawings.replace(UUID.fromString(principal.getSubject()), familyId, memberId, body);
    }
}
