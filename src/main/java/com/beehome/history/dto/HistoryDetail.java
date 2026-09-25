package com.beehome.history.dto;

import com.beehome.dailyexecution.dto.ExecutionResponse;
import com.beehome.study.dto.StudySessionResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HistoryDetail(LocalDate date, UUID childId, @Schema(nullable = true) ExecutionResponse routine,
        List<StudySessionResponse> studies, List<Reading> reading, int readingPage, int readingSize,
        boolean readingHasNext, List<Photo> photos) {
    @Schema(name = "HistoryReadingSession", description = "Reading session on the requested date; unknown measurements remain null")
    public record Reading(UUID id, UUID childBookId, UUID bookId, String bookTitle,
            @Schema(nullable = true) Integer minutes, @Schema(nullable = true) Integer pagesRead) {}
    public record Photo(UUID id, List<Media> media) {
        public record Media(UUID id, int position) {}
    }
}
