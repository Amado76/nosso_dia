package com.nossodia.familymember.exception;

import com.nossodia.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class FamilyMemberException extends ApiException {
    private FamilyMemberException(HttpStatus status, String code, String key) { super(status, code, key); }

    public static FamilyMemberException notFound() {
        return new FamilyMemberException(HttpStatus.NOT_FOUND, "FAMILY_MEMBER_NOT_FOUND", "error.family-member.not-found");
    }

    public static FamilyMemberException alreadyLinked() {
        return new FamilyMemberException(HttpStatus.CONFLICT, "FAMILY_MEMBER_ALREADY_LINKED", "error.family-member.already-linked");
    }

    public static FamilyMemberException userAlreadyLinked() {
        return new FamilyMemberException(HttpStatus.CONFLICT, "USER_ALREADY_LINKED_TO_FAMILY_MEMBER", "error.family-member.user-already-linked");
    }

    public static FamilyMemberException inactive() {
        return new FamilyMemberException(HttpStatus.CONFLICT, "FAMILY_MEMBER_INACTIVE", "error.family-member.inactive");
    }

    public static FamilyMemberException invalid(String field) {
        return new FamilyMemberException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "validation.family-member." + field);
    }
}
