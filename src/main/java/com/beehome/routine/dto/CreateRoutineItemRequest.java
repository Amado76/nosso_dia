package com.beehome.routine.dto;

import com.fasterxml.jackson.annotation.*;
import com.beehome.shared.dto.JsonFields;
import com.beehome.shared.exception.InputException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.*;

@Schema(description = "Create; required fields must be supplied; active defaults true", requiredProperties = {"familyMemberId", "title", "sortOrder"})
public record CreateRoutineItemRequest(UUID familyMemberId,
        @Schema(minLength = 1, maxLength = 120, description = "Stripped nonblank title") String title,
        @Schema(nullable = true, maxLength = 2000, description = "Stripped; blank becomes null") String description,
        @Schema(type = "string", pattern = "^[0-9]{2}:[0-9]{2}$", nullable = true) LocalTime scheduledTime,
        @Schema(minimum = "0", maximum = "2147483647") Integer sortOrder) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CreateRoutineItemRequest fromJson(Map<String, Object> values) {
        JsonFields.only(values, "familyMemberId", "title", "description", "scheduledTime", "sortOrder");
        return new CreateRoutineItemRequest(JsonFields.uuid(values.get("familyMemberId")), JsonFields.string(values.get("title")), JsonFields.string(values.get("description")), JsonFields.time(values.get("scheduledTime")), JsonFields.integer(values.get("sortOrder")));
    }
}
