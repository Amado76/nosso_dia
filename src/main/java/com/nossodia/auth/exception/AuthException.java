package com.nossodia.auth.exception;

import com.nossodia.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public class AuthException extends ApiException {
    public AuthException(String code, String key) { super(HttpStatus.UNAUTHORIZED, code, key); }
    public AuthException(HttpStatus status, String code, String key) { super(status, code, key); }
}
