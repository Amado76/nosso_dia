package com.beehome.shared.exception;

import org.springframework.http.HttpStatus;

public abstract class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final Object[] arguments;

    protected ApiException(HttpStatus status, String code, String messageKey, Object... arguments) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.arguments = arguments.clone();
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public Object[] getArguments() {
        return arguments.clone();
    }
}
