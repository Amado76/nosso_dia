package com.beehome.reading.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import com.beehome.shared.dto.JsonFields;
import com.fasterxml.jackson.annotation.*;
import java.util.*;
@Schema(description="Create or replace metadata. Title is required; omitted optional fields, including tagIds, clear on PUT. Cover must be a family IMAGE.", requiredProperties={"title"})
public record BookRequest(String title, String author, String isbn, Integer totalPages, UUID coverMediaId,
        List<UUID> tagIds) {
    @JsonCreator(mode=JsonCreator.Mode.DELEGATING)
    public static BookRequest fromJson(Map<String,Object> v) {
        JsonFields.only(v,"title","author","isbn","totalPages","coverMediaId","tagIds");
        return new BookRequest(JsonFields.string(v.get("title")),JsonFields.string(v.get("author")),
                JsonFields.string(v.get("isbn")),JsonFields.integer(v.get("totalPages")),JsonFields.uuid(v.get("coverMediaId")),
                JsonFields.uuidList(v,"tagIds"));
    }
}
