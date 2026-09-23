package com.beehome.routine.dto;

import com.fasterxml.jackson.annotation.*;
import com.beehome.shared.dto.JsonFields;
import com.beehome.shared.exception.InputException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.*;
import java.util.*;

@Schema(description = "Nonempty partial edit; omission preserves, null clears only nullable fields")
public record PatchRoutineRequest(@Schema(minLength = 1, maxLength = 120, description = "Stripped nonblank name; duplicates allowed") String name,
        @io.swagger.v3.oas.annotations.media.ArraySchema(minItems = 1, maxItems = 7, uniqueItems = true) Set<DayOfWeek> daysOfWeek,
        @Schema(nullable = true, description = "Inclusive start; null is open-ended") LocalDate startDate,
        @Schema(nullable = true, description = "Inclusive end; must not precede start") LocalDate endDate,
        @JsonIgnore @Schema(hidden = true) Set<String> fields) {
    public PatchRoutineRequest { fields = Set.copyOf(fields); }
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PatchRoutineRequest fromJson(Map<String, Object> values) {
        JsonFields.only(values, "name", "daysOfWeek", "startDate", "endDate");
        return new PatchRoutineRequest(JsonFields.string(values.get("name")), days(values.get("daysOfWeek")), JsonFields.date(values.get("startDate")), JsonFields.date(values.get("endDate")), values.keySet());
    }
    private static Set<DayOfWeek> days(Object value) {
        if (value == null) return null;
        if (!(value instanceof List<?> list) || list.isEmpty() || list.size() > 7) throw new InputException();
        var days = EnumSet.noneOf(DayOfWeek.class);
        for (Object entry : list) {
            try { if (entry == null || !days.add(DayOfWeek.valueOf(JsonFields.string(entry)))) throw new InputException(); }
            catch (IllegalArgumentException e) { throw new InputException(); }
        }
        return days;
    }
}
