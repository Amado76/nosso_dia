package com.beehome.familymember.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.beehome.familymember.entity.MemberType;
import com.beehome.familymember.exception.FamilyMemberException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

@Schema(description = "Nonempty subset of editable fields. Omitted fields are preserved; null clears only birthDate and color.")
public record PatchFamilyMemberRequest(
        @Schema(description = "Non-null, nonblank after strip(), at most 120 UTF-16 code units") String name,
        @Schema(description = "Non-null explicit classification") MemberType memberType,
        @Schema(nullable = true, description = "ISO date no later than today in UTC; null clears") LocalDate birthDate,
        @Schema(nullable = true, pattern = "^#[0-9a-fA-F]{6}$", description = "Null clears") String color,
        @JsonIgnore @Schema(hidden = true) Set<String> fields) {
    public PatchFamilyMemberRequest { fields = Set.copyOf(fields); }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static PatchFamilyMemberRequest fromJson(Map<String, Object> values) {
        if (!Set.of("name", "memberType", "birthDate", "color").containsAll(values.keySet())) {
            throw new IllegalArgumentException("Unsupported member request field");
        }
        return new PatchFamilyMemberRequest(MemberJson.string(values.get("name")), MemberJson.type(values.get("memberType")),
                MemberJson.date(values.get("birthDate")), MemberJson.string(values.get("color")), values.keySet());
    }

    public void validatePresence() {
        if (fields.isEmpty()) throw FamilyMemberException.invalid("patch");
    }
}
