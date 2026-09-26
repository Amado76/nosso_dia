package com.beehome.photorecord.dto;

import com.beehome.shared.exception.InputException;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import java.time.LocalDate;
import java.util.*;

@Schema(requiredProperties={"date", "mediaIds"})
public record PhotoRecordRequest(@Schema(description="ISO calendar date", example="2026-09-21") LocalDate date,
        @Schema(nullable=true, maxLength=2000) String description,
        @ArraySchema(minItems=1, maxItems=20, schema=@Schema(description="Unique image media ID")) List<UUID> mediaIds,
        @ArraySchema(maxItems=100, schema=@Schema(description="Unique family tag ID")) List<UUID> tagIds) {
    public List<UUID> normalizedTagIds() { return tagIds == null ? List.of() : tagIds; }
    public void validate() {
        if (date == null || mediaIds == null || mediaIds.isEmpty() || mediaIds.size() > 20 ||
                mediaIds.contains(null) || new HashSet<>(mediaIds).size() != mediaIds.size() ||
                (description != null && description.strip().length() > 2000) ||
                (tagIds != null && (tagIds.size() > 100 || tagIds.contains(null) || new HashSet<>(tagIds).size() != tagIds.size())))
            throw new InputException();
    }
    public String normalizedDescription() {
        if (description == null || description.isBlank()) return null;
        return description.strip();
    }
}
