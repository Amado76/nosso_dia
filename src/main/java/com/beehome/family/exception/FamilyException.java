package com.beehome.family.exception;

import com.beehome.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class FamilyException extends ApiException {
    private FamilyException(HttpStatus status, String code, String key) { super(status, code, key); }

    public static FamilyException notFound() {
        return new FamilyException(HttpStatus.NOT_FOUND, "FAMILY_NOT_FOUND", "error.family.not-found");
    }

    public static FamilyException forbidden() {
        return new FamilyException(HttpStatus.FORBIDDEN, "FORBIDDEN", "error.auth.forbidden");
    }

    public static FamilyException invalidName() {
        return new FamilyException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "validation.family.name");
    }

    public static FamilyException invalidPage() {
        return new FamilyException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "validation.family.pagination");
    }
}
