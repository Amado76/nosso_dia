package com.beehome.reading.dto;
import com.beehome.shared.dto.JsonFields;
import com.beehome.shared.exception.InputException;
import com.beehome.reading.entity.ChildBookStatus;
import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;
import java.time.LocalDate;
@Schema(description="Create: bookId required, status defaults to PLANNED. Patch: nonempty subset of status and dates; omitted dates preserved, null clears. COMPLETED requires completedOn.")
public record ChildBookRequest(UUID bookId, ChildBookStatus status, LocalDate startedOn, LocalDate completedOn,
        @JsonIgnore @Schema(hidden=true) Set<String> fields) {
    public ChildBookRequest { fields=Set.copyOf(fields); }
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static ChildBookRequest fromJson(Map<String,Object> v) {
        JsonFields.only(v,"bookId","status","startedOn","completedOn");
        ChildBookStatus status=null;
        try { if(v.get("status")!=null) status=ChildBookStatus.valueOf(JsonFields.string(v.get("status"))); }
        catch(IllegalArgumentException e) { throw new InputException(); }
        return new ChildBookRequest(JsonFields.uuid(v.get("bookId")),status,JsonFields.date(v.get("startedOn")),
                JsonFields.date(v.get("completedOn")),v.keySet());
    }
}
