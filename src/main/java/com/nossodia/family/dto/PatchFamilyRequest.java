package com.nossodia.family.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nossodia.shared.dto.JsonFields;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Nonempty subset of name and timezone; omission preserves; null is invalid")
public record PatchFamilyRequest(String name, String timezone,
        @JsonIgnore @Schema(hidden = true) Set<String> fields) {
    public PatchFamilyRequest { fields = Set.copyOf(fields); }
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PatchFamilyRequest fromJson(Map<String, Object> values) {
        JsonFields.only(values, "name", "timezone");
        return new PatchFamilyRequest(JsonFields.string(values.get("name")), JsonFields.string(values.get("timezone")), values.keySet());
    }
}
