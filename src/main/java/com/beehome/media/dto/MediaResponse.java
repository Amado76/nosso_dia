package com.beehome.media.dto;

import com.beehome.media.entity.Media;
import java.time.Instant;
import java.util.UUID;

public record MediaResponse(UUID id, String type, String mimeType, long sizeBytes, Instant createdAt) {
    public static MediaResponse from(Media media) {
        return new MediaResponse(media.getId(), media.getType(), media.getMimeType(), media.getSizeBytes(), media.getCreatedAt());
    }
}
