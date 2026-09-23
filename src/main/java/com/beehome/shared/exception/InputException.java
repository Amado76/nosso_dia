package com.beehome.shared.exception;

import org.springframework.http.HttpStatus;

public final class InputException extends ApiException {
    public InputException() { super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "error.validation"); }
}
