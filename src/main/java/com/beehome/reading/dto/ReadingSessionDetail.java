package com.beehome.reading.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
import java.time.*;
@Schema(description="Dated reading activity and current book metadata. Measurements and notes are nullable; counts remain independent of current position.")
public record ReadingSessionDetail(UUID id,UUID familyId,UUID childId,UUID childBookId,BookResponse book,LocalDate date,
        Integer minutes,Integer pagesRead,Integer startPage,Integer endPage,String notes,UUID createdBy,Instant createdAt,Instant updatedAt) {}
