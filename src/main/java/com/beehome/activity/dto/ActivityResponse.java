package com.beehome.activity.dto;

import com.beehome.activity.entity.ExtracurricularActivity;
import java.time.Instant;
import java.util.UUID;

public record ActivityResponse(UUID id, UUID familyId, String name, String description, int sortOrder,
        boolean active, Instant createdAt, Instant updatedAt) {
    public static ActivityResponse from(ExtracurricularActivity a) {
        return new ActivityResponse(a.getId(), a.getFamilyId(), a.getName(), a.getDescription(),
                a.getSortOrder(), a.isActive(), a.getCreatedAt(), a.getUpdatedAt());
    }
}
