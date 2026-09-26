package com.beehome.activity.exception;

import com.beehome.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class ActivityException extends ApiException {
    private ActivityException(HttpStatus status, String code, String key) { super(status, code, "error.activity." + key); }
    public static ActivityException missing() { return new ActivityException(HttpStatus.NOT_FOUND, "ACTIVITY_NOT_FOUND", "not-found"); }
    public static ActivityException recordMissing() { return new ActivityException(HttpStatus.NOT_FOUND, "ACTIVITY_RECORD_NOT_FOUND", "record-not-found"); }
    public static ActivityException duplicate() { return new ActivityException(HttpStatus.CONFLICT, "ACTIVITY_DUPLICATE", "duplicate"); }
}
