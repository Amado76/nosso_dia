package com.beehome.drawing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"familyId", "memberId", "surface", "formatVersion", "revision", "strokes", "createdAt", "updatedAt"})
public record DrawingResponse(UUID familyId, UUID memberId, String surface, int formatVersion,
        long revision, List<DrawingStroke> strokes,
        @Schema(nullable = true) Instant createdAt, @Schema(nullable = true) Instant updatedAt) {}
