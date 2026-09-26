package com.beehome.drawing.exception;

import com.beehome.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class DrawingException extends ApiException {
    private DrawingException(HttpStatus status, String code, String key) { super(status, code, "error.drawing." + key); }
    public static DrawingException version() { return new DrawingException(HttpStatus.BAD_REQUEST, "DRAWING_VERSION_UNSUPPORTED", "version"); }
    public static DrawingException format() { return new DrawingException(HttpStatus.BAD_REQUEST, "DRAWING_INVALID_FORMAT", "format"); }
    public static DrawingException stroke() { return new DrawingException(HttpStatus.BAD_REQUEST, "DRAWING_INVALID_STROKE", "stroke"); }
    public static DrawingException point() { return new DrawingException(HttpStatus.BAD_REQUEST, "DRAWING_INVALID_POINT", "point"); }
    public static DrawingException tooLarge() { return new DrawingException(HttpStatus.PAYLOAD_TOO_LARGE, "DRAWING_TOO_LARGE", "too-large"); }
    public static DrawingException conflict() { return new DrawingException(HttpStatus.CONFLICT, "DRAWING_VERSION_CONFLICT", "conflict"); }
}
