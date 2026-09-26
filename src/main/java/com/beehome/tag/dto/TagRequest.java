package com.beehome.tag.dto;

import com.beehome.shared.dto.JsonFields;
import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;

@Schema(description = "POST requires name; PATCH accepts a nonempty subset. Null color clears it; null name is invalid.")
public record TagRequest(@Schema(maxLength = 120, description = "NFC normalized nonblank display name") String name,
        @Schema(nullable = true, pattern = "^#[0-9A-Fa-f]{6}$") String color,
        @JsonIgnore @Schema(hidden = true) Set<String> fields) {
    public TagRequest { fields = Set.copyOf(fields); }
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static TagRequest fromJson(Map<String, Object> values) {
        JsonFields.only(values, "name", "color");
        return new TagRequest(JsonFields.string(values.get("name")), JsonFields.string(values.get("color")), values.keySet());
    }
}
