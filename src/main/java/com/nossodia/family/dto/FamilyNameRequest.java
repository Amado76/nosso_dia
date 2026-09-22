package com.nossodia.family.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FamilyNameRequest(
        @Schema(description = "Family name; surrounding whitespace is stripped", example = "Amado Family", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.family.name}")
        @Size(max = 120, message = "{validation.family.name}") String name) {
    public FamilyNameRequest {
        name = name == null ? null : name.strip();
    }

    // Keep strict input handling local to this API without changing authentication's contract.
    @JsonCreator
    public static FamilyNameRequest fromJson(@JsonProperty("name") Object name) {
        if (name != null && !(name instanceof String)) throw new IllegalArgumentException("Name must be a string");
        return new FamilyNameRequest((String) name);
    }

    @JsonAnySetter
    public void rejectUnknown(String field, Object value) {
        throw new IllegalArgumentException("Unsupported family request field");
    }
}
