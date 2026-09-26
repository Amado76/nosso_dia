package com.beehome.activity.dto;

import com.beehome.activity.entity.ExtracurricularRecord;
import com.beehome.tag.dto.TagSummary;
import java.time.*;
import java.util.*;

public record ActivityRecordResponse(UUID id, UUID childId, LocalDate date, Activity activity,
        String topic, Integer durationMinutes, String description, String comments, String material,
        Integer startPage, Integer endPage, List<TagSummary> tags, UUID createdByUserId,
        Instant createdAt, Instant updatedAt, long version) {
    public record Activity(UUID id, String name) {}
    public static ActivityRecordResponse from(ExtracurricularRecord r, Activity activity, List<TagSummary> tags) {
        return new ActivityRecordResponse(r.getId(), r.getChildId(), r.getDate(), activity,
                r.getTopic(), r.getDurationMinutes(), r.getDescription(), r.getComments(), r.getMaterial(),
                r.getStartPage(), r.getEndPage(), tags, r.getCreatedByUserId(), r.getCreatedAt(), r.getUpdatedAt(), r.getVersion());
    }
}
