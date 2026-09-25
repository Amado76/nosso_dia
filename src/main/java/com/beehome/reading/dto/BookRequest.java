package com.beehome.reading.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import com.beehome.shared.dto.JsonFields;
import com.fasterxml.jackson.annotation.*;
import java.util.*;
@Schema(description="Create or replace metadata. Title is required; optional fields accept null and are cleared when omitted on PUT. Cover must be a family IMAGE.", requiredProperties={"title"})
public record BookRequest(String title, String author, String isbn, Integer totalPages, UUID coverMediaId) {
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static BookRequest fromJson(Map<String,Object> v) {
        JsonFields.only(v,"title","author","isbn","totalPages","coverMediaId");
        return new BookRequest(JsonFields.string(v.get("title")),JsonFields.string(v.get("author")),
                JsonFields.string(v.get("isbn")),JsonFields.integer(v.get("totalPages")),JsonFields.uuid(v.get("coverMediaId")));
    }
}
