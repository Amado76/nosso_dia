package com.beehome.activity.dto;

import com.beehome.shared.dto.JsonFields;
import com.beehome.shared.exception.InputException;
import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.*;

@Schema(description = "Create requires activityId and date. Patch accepts a nonempty subset; null clears optional fields.")
public record ActivityRecordRequest(UUID activityId, LocalDate date, String topic, Integer durationMinutes,
        String description, String comments, String material, Integer startPage, Integer endPage, List<UUID> tagIds,
        @JsonIgnore @Schema(hidden = true) Set<String> fields) {
    public ActivityRecordRequest { fields = Set.copyOf(fields); }
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ActivityRecordRequest fromJson(Map<String,Object> json) {
        JsonFields.only(json, "activityId", "date", "topic", "durationMinutes", "description", "comments", "material", "startPage", "endPage", "tagIds");
        return new ActivityRecordRequest(JsonFields.uuid(json.get("activityId")), JsonFields.date(json.get("date")),
                JsonFields.string(json.get("topic")), JsonFields.integer(json.get("durationMinutes")),
                JsonFields.string(json.get("description")), JsonFields.string(json.get("comments")),
                JsonFields.string(json.get("material")), JsonFields.integer(json.get("startPage")),
                JsonFields.integer(json.get("endPage")), JsonFields.uuidList(json, "tagIds"), json.keySet());
    }
    public void validateCreate() { if (activityId == null || date == null) throw new InputException(); }
    public void validatePatch() {
        if (fields.isEmpty() || (fields.contains("activityId") && activityId == null)
                || (fields.contains("date") && date == null)) throw new InputException();
    }
}
