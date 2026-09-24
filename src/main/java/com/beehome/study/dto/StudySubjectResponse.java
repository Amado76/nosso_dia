package com.beehome.study.dto;
import com.beehome.study.entity.StudySubject;
import java.time.Instant;
import java.util.UUID;
public record StudySubjectResponse(UUID id, UUID familyId, String name, String color, boolean active,
        int sortOrder, Instant createdAt, Instant updatedAt) {
    public static StudySubjectResponse from(StudySubject s) {
        return new StudySubjectResponse(s.getId(), s.getFamilyId(), s.getName(), s.getColor(), s.isActive(),
                s.getSortOrder(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
