package com.nossodia.routine.dto;

import com.fasterxml.jackson.annotation.*;
import com.nossodia.shared.dto.JsonFields;
import com.nossodia.shared.exception.InputException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.*;

@Schema(description = "Nonempty partial edit; omission preserves, null clears only nullable fields")
public record PatchRoutineItemRequest(UUID familyMemberId,
        @Schema(minLength = 1, maxLength = 120, description = "Stripped nonblank title") String title,
        @Schema(nullable = true, maxLength = 2000, description = "Stripped; blank becomes null") String description,
        @Schema(type = "string", pattern = "^[0-9]{2}:[0-9]{2}$", nullable = true) LocalTime scheduledTime,
        @Schema(minimum = "0", maximum = "2147483647") Integer sortOrder,
        @JsonIgnore @Schema(hidden = true) Set<String> fields) {
    public PatchRoutineItemRequest { fields = Set.copyOf(fields); }
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PatchRoutineItemRequest fromJson(Map<String, Object> values) {
        JsonFields.only(values, "familyMemberId", "title", "description", "scheduledTime", "sortOrder");
        return new PatchRoutineItemRequest(JsonFields.uuid(values.get("familyMemberId")), JsonFields.string(values.get("title")), JsonFields.string(values.get("description")), JsonFields.time(values.get("scheduledTime")), JsonFields.integer(values.get("sortOrder")), values.keySet());
    }
}
