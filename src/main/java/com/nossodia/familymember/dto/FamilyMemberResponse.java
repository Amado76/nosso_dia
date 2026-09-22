package com.nossodia.familymember.dto;

import com.nossodia.familymember.entity.FamilyMember;
import com.nossodia.familymember.entity.MemberType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Schema(requiredProperties = {"id", "familyId", "name", "memberType", "birthDate", "color", "avatarReference", "linkedUser", "active", "createdAt", "updatedAt"})
public record FamilyMemberResponse(UUID id, UUID familyId, String name, MemberType memberType,
        @Schema(nullable = true) LocalDate birthDate, @Schema(nullable = true) String color,
        @Schema(nullable = true, description = "Reserved for media; always null in BE-03") String avatarReference,
        boolean linkedUser, boolean active, Instant createdAt, Instant updatedAt) {
    public static FamilyMemberResponse from(FamilyMember member) {
        return new FamilyMemberResponse(member.getId(), member.getFamilyId(), member.getName(), member.getMemberType(),
                member.getBirthDate(), member.getColor(), member.getAvatarReference(), member.getLinkedUserId() != null,
                member.isActive(), member.getCreatedAt(), member.getUpdatedAt());
    }
}
