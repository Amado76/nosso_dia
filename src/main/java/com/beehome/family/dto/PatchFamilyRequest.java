package com.beehome.family.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.beehome.shared.dto.JsonFields;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Name is required; null and empty patches are invalid")
public record PatchFamilyRequest(String name,
        @JsonIgnore @Schema(hidden = true) Set<String> fields) {
    public PatchFamilyRequest { fields = Set.copyOf(fields); }
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PatchFamilyRequest fromJson(Map<String, Object> values) {
        JsonFields.only(values, "name");
        return new PatchFamilyRequest(JsonFields.string(values.get("name")), values.keySet());
    }
}
