package com.beehome.tag.exception;

import com.beehome.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class TagException extends ApiException {
    private static final long serialVersionUID = 1L;
    private TagException(HttpStatus status, String code, String key) { super(status, code, key); }
    public static TagException notFound() { return new TagException(HttpStatus.NOT_FOUND, "TAG_NOT_FOUND", "error.tag.not-found"); }
    public static TagException duplicate() { return new TagException(HttpStatus.CONFLICT, "TAG_NAME_CONFLICT", "error.tag.duplicate"); }
}
