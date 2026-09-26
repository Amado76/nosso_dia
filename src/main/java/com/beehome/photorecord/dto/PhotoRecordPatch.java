package com.beehome.photorecord.dto;

import com.beehome.shared.dto.JsonFields;
import com.beehome.shared.exception.InputException;
import java.time.LocalDate;
import java.util.*;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description="At least one field; omitted values are preserved")
public record PhotoRecordPatch(@Schema(hidden=true) Map<String,Object> fields,
        @Schema(description="Event date; cannot be null") LocalDate date,
        @Schema(description="Nullable; blank becomes null", maxLength=2000, nullable=true) String description,
        @Schema(description="Empty array clears tags; omitted preserves tags") List<UUID> tagIds) {
    public static PhotoRecordPatch from(Map<String,Object> values) {
        JsonFields.only(values,"date","description","tagIds");
        if (values.isEmpty()) throw new InputException();
        LocalDate date=values.containsKey("date") ? JsonFields.date(values.get("date")) : null;
        if (values.containsKey("date")) JsonFields.required(date);
        String description=values.containsKey("description") ? JsonFields.text(JsonFields.string(values.get("description")),2000,false) : null;
        List<UUID> tags=values.containsKey("tagIds") ? JsonFields.uuidList(values,"tagIds") : null;
        if (tags!=null && (tags.contains(null) || new HashSet<>(tags).size()!=tags.size())) throw new InputException();
        return new PhotoRecordPatch(values,date,description,tags);
    }
}
