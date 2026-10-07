package com.beehome.family.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.beehome.shared.dto.JsonFields;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"name"})
public record CreateFamilyRequest(
        @Schema(maxLength = 120, example = "Amado Family") @jakarta.validation.constraints.NotBlank(message = "{validation.family.name}")
        @jakarta.validation.constraints.Size(max = 120, message = "{validation.family.name}") String name) {
    public CreateFamilyRequest { name = name == null ? null : name.strip(); }
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CreateFamilyRequest fromJson(Map<String, Object> values) {
        JsonFields.only(values, "name");
        return new CreateFamilyRequest(JsonFields.string(values.get("name")));
    }
}
