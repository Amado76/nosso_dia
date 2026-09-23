package com.beehome.familymember.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.beehome.familymember.entity.MemberType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateFamilyMemberRequest(
        @Schema(description = "Name after strip(), at most 120 UTF-16 code units", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.family-member.name}") @Size(max = 120, message = "{validation.family-member.name}") String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.family-member.member-type}") MemberType memberType,
        @Schema(description = "ISO date, no later than today in UTC", nullable = true) LocalDate birthDate,
        @Schema(description = "Six hex digits after #; returned uppercase", nullable = true, pattern = "^#[0-9a-fA-F]{6}$") String color) {
    public CreateFamilyMemberRequest { name = name == null ? null : name.strip(); }

    @JsonCreator
    public static CreateFamilyMemberRequest fromJson(@JsonProperty("name") Object name,
            @JsonProperty("memberType") Object memberType, @JsonProperty("birthDate") Object birthDate,
            @JsonProperty("color") Object color) {
        return new CreateFamilyMemberRequest(MemberJson.string(name), MemberJson.type(memberType),
                MemberJson.date(birthDate), MemberJson.string(color));
    }

    @JsonAnySetter
    public void rejectUnknown(String field, Object value) { throw new IllegalArgumentException("Unsupported member request field"); }
}
