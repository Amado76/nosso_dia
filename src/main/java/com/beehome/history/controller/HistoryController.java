package com.beehome.history.controller;

import com.beehome.history.dto.*;
import com.beehome.history.service.HistoryService;
import com.beehome.history.pdf.DailyReportPdfRenderer;
import com.beehome.shared.dto.JsonFields;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import java.text.Normalizer;
import org.springframework.http.*;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/families/{familyId}/children/{childId}")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "400", description = "Invalid date, period, month, or pagination", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
@ApiResponse(responseCode = "401", description = "Bearer authentication required")
@ApiResponse(responseCode = "404", description = "Family or child missing or inaccessible")
public class HistoryController {
    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);
    private final HistoryService history;
    private final DailyReportPdfRenderer pdf;
    public HistoryController(HistoryService history, DailyReportPdfRenderer pdf) { this.history = history; this.pdf = pdf; }
    private static UUID user(Jwt jwt) { return UUID.fromString(jwt.getSubject()); }

    @GetMapping("/history/{date}")
    @Operation(summary = "Read one child's day with paginated reading sessions, without creating records")
    public HistoryDetail detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @PathVariable UUID childId,
            @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(defaultValue = "0") int readingPage,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(defaultValue = "20") int readingSize) {
        return history.detail(user(jwt), familyId, childId, JsonFields.date(date), readingPage, readingSize);
    }
    @GetMapping(value = "/history/{date}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download an on-demand daily report PDF")
    @ApiResponse(responseCode = "200", description = "On-demand daily report",
            content = @Content(mediaType = "application/pdf", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "422", description = "Daily report exceeds the item or PDF byte budget",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Daily PDF generation is at capacity",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @PathVariable UUID childId,
            @Parameter(schema = @Schema(type = "string", format = "date")) @PathVariable String date) {
        long start = System.nanoTime();
        var day = history.reportDetail(user(jwt), familyId, childId, JsonFields.date(date));
        String slug = Normalizer.normalize(day.childName(), Normalizer.Form.NFKD).replaceAll("\\p{M}+", "")
                .toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (slug.isBlank()) slug = "child";
        String filename = "daily-report-" + slug + "-" + day.date() + ".pdf";
        byte[] body = pdf.render(user(jwt), familyId, day);
        log.info("Daily PDF request completed: durationMs={}",
                java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }
    @GetMapping("/calendar")
    @Operation(summary = "List dates with activity and source flags")
    public HistoryCalendar calendar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId,
            @PathVariable UUID childId, @Parameter(required = true) @RequestParam(required = false) Integer year,
            @Parameter(required = true) @RequestParam(required = false) Integer month) {
        return history.calendar(user(jwt), familyId, childId, year, month);
    }
    @GetMapping("/history")
    @Operation(summary = "Page active days in an inclusive period, newest first")
    public HistoryPage history(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @PathVariable UUID childId,
            @Parameter(required = true, schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String from,
            @Parameter(required = true, schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String to,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(defaultValue = "0") int page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(defaultValue = "20") int size) {
        return history.history(user(jwt), familyId, childId, JsonFields.date(from), JsonFields.date(to), page, size);
    }
    @GetMapping("/reports")
    @Operation(summary = "Calculate live totals for an inclusive period")
    public HistoryReport report(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID familyId, @PathVariable UUID childId,
            @Parameter(required = true, schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String from,
            @Parameter(required = true, schema = @Schema(type = "string", format = "date")) @RequestParam(required = false) String to) {
        return history.report(user(jwt), familyId, childId, JsonFields.date(from), JsonFields.date(to));
    }
}
