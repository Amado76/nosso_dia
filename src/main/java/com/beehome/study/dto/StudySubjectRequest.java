package com.beehome.study.dto;

import com.beehome.shared.dto.JsonFields;
import com.beehome.shared.exception.InputException;
import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;

@Schema(description = "Create: required name, optional color and sortOrder. Patch: nonempty subset; null clears only color.")
public record StudySubjectRequest(String name, String color, Integer sortOrder,
        @JsonIgnore @Schema(hidden = true) Set<String> fields) {
    public StudySubjectRequest { fields = Set.copyOf(fields); }
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static StudySubjectRequest fromJson(Map<String,Object> values) {
        JsonFields.only(values, "name", "color", "sortOrder");
        return new StudySubjectRequest(JsonFields.string(values.get("name")), JsonFields.string(values.get("color")),
                JsonFields.integer(values.get("sortOrder")), values.keySet());
    }
    public void validatePatch() {
        if (fields.isEmpty() || (fields.contains("name") && name == null)
                || (fields.contains("sortOrder") && sortOrder == null)) throw new InputException();
    }
}
