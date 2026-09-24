package com.beehome.media.exception;

import com.beehome.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class MediaException extends ApiException {
    private MediaException(HttpStatus status, String code, String key) { super(status, code, "error.media." + key); }
    public static MediaException notFound() { return new MediaException(HttpStatus.NOT_FOUND, "MEDIA_NOT_FOUND", "not-found"); }
    public static MediaException inUse() { return new MediaException(HttpStatus.CONFLICT, "MEDIA_IN_USE", "in-use"); }
    public static MediaException invalidType() { return new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_INVALID_TYPE", "invalid-type"); }
    public static MediaException tooLarge() { return new MediaException(HttpStatus.BAD_REQUEST, "MEDIA_TOO_LARGE", "too-large"); }
    public static MediaException failed() { return new MediaException(HttpStatus.INTERNAL_SERVER_ERROR, "MEDIA_UPLOAD_FAILED", "failed"); }
}
