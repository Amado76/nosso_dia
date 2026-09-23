package com.beehome.dailyplan.dto;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.beehome.shared.dto.JsonFields;
import com.beehome.shared.exception.InputException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(requiredProperties = {"note"})
public record DailyNoteRequest(@Schema(nullable = true, maxLength = 2000) String note) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static DailyNoteRequest fromJson(Map<String, Object> values) {
        JsonFields.only(values, "note");
        if (!values.containsKey("note")) throw new InputException();
        return new DailyNoteRequest(JsonFields.string(values.get("note")));
    }
}
