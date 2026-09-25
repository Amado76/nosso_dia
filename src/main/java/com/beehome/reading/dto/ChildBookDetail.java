package com.beehome.reading.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import java.time.*;
import com.beehome.reading.entity.ChildBookStatus;
@Schema(description="Journey and current book metadata. Dates and progress are nullable; currentPage is latest recorded endPage, never summed pagesRead.")
public record ChildBookDetail(UUID id,UUID familyId,UUID childId,UUID bookId,BookResponse book,ChildBookStatus status,
        LocalDate startedOn,LocalDate completedOn,Integer currentPage,Double progressPercentage,Instant createdAt,Instant updatedAt) {}
