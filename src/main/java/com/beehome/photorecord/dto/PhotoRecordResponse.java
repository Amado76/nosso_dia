package com.beehome.photorecord.dto;

import com.beehome.photorecord.entity.PhotoRecord;
import java.time.*;
import java.util.*;

public record PhotoRecordResponse(UUID id, UUID childId, LocalDate date, String description,
        List<MediaEntry> media, Instant createdAt, Instant updatedAt) {
    public record MediaEntry(UUID id, String type, int position) {}
    public static PhotoRecordResponse from(PhotoRecord record, List<MediaEntry> entries) {
        return new PhotoRecordResponse(record.getId(), record.getChildId(), record.getDate(), record.getDescription(),
                entries, record.getCreatedAt(), record.getUpdatedAt());
    }
}
