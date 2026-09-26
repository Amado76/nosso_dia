package com.beehome.activity.dto;

import com.beehome.shared.dto.JsonFields;
import com.beehome.shared.exception.InputException;
import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;

@Schema(description = "Create requires name. Patch accepts a nonempty subset; null clears description only.")
public record ActivityRequest(String name, String description, Integer sortOrder,
        @JsonIgnore @Schema(hidden = true) Set<String> fields) {
    public ActivityRequest { fields = Set.copyOf(fields); }
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ActivityRequest fromJson(Map<String,Object> json) {
        JsonFields.only(json, "name", "description", "sortOrder");
        return new ActivityRequest(JsonFields.string(json.get("name")), JsonFields.string(json.get("description")),
                JsonFields.integer(json.get("sortOrder")), json.keySet());
    }
    public void validatePatch() {
        if (fields.isEmpty() || (fields.contains("name") && name == null)
                || (fields.contains("sortOrder") && sortOrder == null)) throw new InputException();
    }
}
