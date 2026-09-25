package com.beehome.reading.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
@Schema(description="Live totals for inclusive dates up to app.reports.max-period-days (default 366). Books counts unique Book IDs with sessions; booksCompleted counts completed journeys.", requiredProperties={"from","to","sessions","totalMinutes","pagesRead","books","booksCompleted"})
public record ReadingSummary(LocalDate from,LocalDate to,long sessions,long totalMinutes,long pagesRead,long books,long booksCompleted) {}
