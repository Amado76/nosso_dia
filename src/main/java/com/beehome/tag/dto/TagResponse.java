package com.beehome.tag.dto;

import com.beehome.tag.entity.Tag;
import java.time.Instant;
import java.util.UUID;

public record TagResponse(UUID id, UUID familyId, String name, String color, Instant createdAt, Instant updatedAt) {
    public static TagResponse from(Tag tag) {
        return new TagResponse(tag.getId(), tag.getFamilyId(), tag.getName(), tag.getColor(), tag.getCreatedAt(), tag.getUpdatedAt());
    }
}
