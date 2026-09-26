package com.beehome.study.dto;

import com.beehome.shared.dto.JsonFields;
import com.beehome.shared.exception.InputException;
import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.*;

@Schema(description = "Start accepts optional subjectId, dailyExecutionItemId, title, notes, details, and tagIds. Manual creation requires date and accepts either legacy durationSeconds or optional positive durationMinutes. Patch accepts a nonempty subset; date and duration are editable only for completed manual sessions. Omitted tagIds preserves assignments; [] clears them.")
public record StudySessionRequest(UUID subjectId, UUID dailyExecutionItemId, String title, String notes,
        LocalDate date, Integer durationSeconds, Integer durationMinutes, List<UUID> tagIds, String topic, String description,
        String comments, String material, Integer startPage, Integer endPage,
        @JsonIgnore @Schema(hidden = true) Set<String> fields) {
    public StudySessionRequest { fields = Set.copyOf(fields); }
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static StudySessionRequest fromJson(Map<String,Object> values) {
        JsonFields.only(values, "subjectId", "dailyExecutionItemId", "title", "notes", "date", "durationSeconds", "durationMinutes", "tagIds",
                "topic", "description", "comments", "material", "startPage", "endPage");
        return new StudySessionRequest(JsonFields.uuid(values.get("subjectId")), JsonFields.uuid(values.get("dailyExecutionItemId")),
                JsonFields.text(JsonFields.string(values.get("title")), 120, false),
                JsonFields.text(JsonFields.string(values.get("notes")), 10000, false),
                JsonFields.date(values.get("date")), JsonFields.integer(values.get("durationSeconds")),
                JsonFields.integer(values.get("durationMinutes")), tagIds(values),
                JsonFields.text(JsonFields.string(values.get("topic")), 120, false),
                JsonFields.text(JsonFields.string(values.get("description")), 10000, false),
                JsonFields.text(JsonFields.string(values.get("comments")), 10000, false),
                JsonFields.text(JsonFields.string(values.get("material")), 1000, false),
                JsonFields.integer(values.get("startPage")), JsonFields.integer(values.get("endPage")), values.keySet());
    }
    private static List<UUID> tagIds(Map<String,Object> values) {
        if (!values.containsKey("tagIds")) return List.of();
        if (!(values.get("tagIds") instanceof List<?> ids) || ids.size()>100) throw new InputException();
        return ids.stream().map(JsonFields::uuid).toList();
    }
    public void validateStart() {
        if (fields.contains("date") || fields.contains("durationSeconds") || fields.contains("durationMinutes")) throw new InputException();
    }
    public void validateManual() {
        if (date == null || (durationSeconds != null && durationMinutes != null)
                || (durationMinutes != null && durationMinutes <= 0)) throw new InputException();
    }
    public void validatePatch() {
        if (fields.isEmpty() || (fields.contains("date") && date == null)
                || (fields.contains("durationSeconds") && durationSeconds == null)
                || (fields.contains("durationMinutes") && durationMinutes != null && durationMinutes <= 0)
                || (fields.contains("durationSeconds") && fields.contains("durationMinutes"))) throw new InputException();
    }
    public int finalDurationSeconds() {
        if (durationMinutes != null) {
            if (durationMinutes > com.beehome.study.entity.StudySession.MAX_SECONDS / 60) throw new InputException();
            return durationMinutes * 60;
        }
        return durationSeconds == null ? 0 : durationSeconds;
    }
    public boolean usesReportFields() {
        return fields.stream().anyMatch(field -> Set.of("durationMinutes", "topic", "description", "comments",
                "material", "startPage", "endPage").contains(field));
    }
}
