package com.beehome.reading.dto;
import com.beehome.shared.dto.JsonFields;
import com.beehome.shared.exception.InputException;
import com.fasterxml.jackson.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.*;
import java.time.LocalDate;
@Schema(description="Create: childBookId and date required. PUT: date required, childBookId forbidden, omitted measurements cleared. Positions must be paired; pagesRead=endPage-startPage.")
public record ReadingSessionRequest(UUID childBookId, LocalDate date, Integer minutes, Integer pagesRead,
        Integer startPage, Integer endPage, String notes, @JsonIgnore @Schema(hidden=true) Set<String> fields) {
    public ReadingSessionRequest { fields=Set.copyOf(fields); }
    private static Integer number(Object v) { if(v==null || v instanceof Integer) return (Integer)v; throw new InputException(); }
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static ReadingSessionRequest fromJson(Map<String,Object> v) {
        JsonFields.only(v,"childBookId","date","minutes","pagesRead","startPage","endPage","notes");
        return new ReadingSessionRequest(JsonFields.uuid(v.get("childBookId")),JsonFields.date(v.get("date")),
                number(v.get("minutes")),number(v.get("pagesRead")),number(v.get("startPage")),number(v.get("endPage")),
                JsonFields.string(v.get("notes")),v.keySet());
    }
}
