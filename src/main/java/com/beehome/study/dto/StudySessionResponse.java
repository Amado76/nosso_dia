package com.beehome.study.dto;
import com.beehome.study.entity.*;
import java.time.*;
import java.util.UUID;
import java.util.List;
import com.beehome.tag.dto.TagSummary;
public record StudySessionResponse(UUID id, UUID familyId, UUID familyMemberId, UUID subjectId,
        UUID dailyExecutionItemId, String subjectNameSnapshot, LocalDate date, String title, String notes,
        EntryMode entryMode, StudyStatus status, Instant startedAt, Instant currentRunStartedAt,
        Instant endedAt, int accumulatedDurationSeconds, UUID createdByUserId,
        Instant createdAt, Instant updatedAt, long version, List<TagSummary> tags,
        String subjectName, String topic, String description, String comments, String material,
        Integer startPage, Integer endPage, Integer durationMinutes) {
    public static StudySessionResponse from(StudySession s) {
        return from(s,List.of());
    }
    public static StudySessionResponse from(StudySession s,List<TagSummary> tags) {
        return from(s, tags, s.getSubjectNameSnapshot());
    }
    public static StudySessionResponse from(StudySession s,List<TagSummary> tags,String subjectName) {
        return new StudySessionResponse(s.getId(), s.getFamilyId(), s.getFamilyMemberId(), s.getSubjectId(),
                s.getDailyExecutionItemId(), s.getSubjectNameSnapshot(), s.getDate(), s.getTitle(), s.getNotes(),
                s.getEntryMode(), s.getStatus(), s.getStartedAt(), s.getCurrentRunStartedAt(), s.getEndedAt(),
                s.getAccumulatedDurationSeconds(), s.getCreatedByUserId(), s.getCreatedAt(), s.getUpdatedAt(), s.getVersion(), tags,
                subjectName, s.getTopic(), s.getDescription(), s.getComments(), s.getMaterial(), s.getStartPage(), s.getEndPage(),
                s.getEntryMode() == EntryMode.MANUAL && s.getAccumulatedDurationSeconds() > 0
                        && s.getAccumulatedDurationSeconds() % 60 == 0 ? s.getAccumulatedDurationSeconds() / 60 : null);
    }
}
