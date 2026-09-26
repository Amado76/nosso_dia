package com.beehome.history.dto;

import com.beehome.dailyexecution.dto.ExecutionResponse;
import com.beehome.study.dto.StudySessionResponse;
import com.beehome.activity.dto.ActivityRecordResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HistoryDetail(LocalDate date, UUID childId, String childName, @Schema(nullable = true) ExecutionResponse routine,
        List<StudySessionResponse> studies, List<Reading> reading, int readingPage, int readingSize,
        boolean readingHasNext, List<Photo> photos, List<ActivityRecordResponse> extracurricularActivities) {
    @Schema(name = "HistoryReadingSession", description = "Reading session on the requested date; unknown measurements remain null")
    public record Reading(UUID id, UUID childBookId, UUID bookId, String bookTitle,
            @Schema(nullable = true) Integer minutes, @Schema(nullable = true) Integer pagesRead) {}
    public record Photo(UUID id, LocalDate date, String description, List<com.beehome.tag.dto.TagSummary> tags, List<Media> media) {
        public record Media(UUID id, int position) {}
    }
}
