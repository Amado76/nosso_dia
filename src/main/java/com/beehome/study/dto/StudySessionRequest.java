package com.beehome.study.dto;

import com.beehome.shared.dto.JsonFields;
import com.beehome.shared.exception.InputException;
import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.*;

@Schema(description = "Start accepts optional subjectId, dailyExecutionItemId, title, notes, tagIds. Manual creation also requires date and durationSeconds. Patch accepts a nonempty subset; date and durationSeconds only for completed manual sessions. Omitted tagIds preserves assignments; [] clears them.")
public record StudySessionRequest(UUID subjectId, UUID dailyExecutionItemId, String title, String notes,
        LocalDate date, Integer durationSeconds, List<UUID> tagIds, @JsonIgnore @Schema(hidden = true) Set<String> fields) {
    public StudySessionRequest { fields = Set.copyOf(fields); }
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static StudySessionRequest fromJson(Map<String,Object> values) {
        JsonFields.only(values, "subjectId", "dailyExecutionItemId", "title", "notes", "date", "durationSeconds", "tagIds");
        return new StudySessionRequest(JsonFields.uuid(values.get("subjectId")), JsonFields.uuid(values.get("dailyExecutionItemId")),
                JsonFields.text(JsonFields.string(values.get("title")), 120, false),
                JsonFields.text(JsonFields.string(values.get("notes")), 10000, false),
                JsonFields.date(values.get("date")), JsonFields.integer(values.get("durationSeconds")), tagIds(values), values.keySet());
    }
    private static List<UUID> tagIds(Map<String,Object> values) {
        if (!values.containsKey("tagIds")) return List.of();
        if (!(values.get("tagIds") instanceof List<?> ids) || ids.size()>100) throw new InputException();
        return ids.stream().map(JsonFields::uuid).toList();
    }
    public void validateStart() {
        if (fields.contains("date") || fields.contains("durationSeconds")) throw new InputException();
    }
    public void validateManual() {
        if (date == null || durationSeconds == null) throw new InputException();
    }
    public void validatePatch() {
        if (fields.isEmpty() || (fields.contains("date") && date == null)
                || (fields.contains("durationSeconds") && durationSeconds == null)) throw new InputException();
    }
}
