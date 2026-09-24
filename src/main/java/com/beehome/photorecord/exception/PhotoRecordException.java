package com.beehome.photorecord.exception;

import com.beehome.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class PhotoRecordException extends ApiException {
    private PhotoRecordException(HttpStatus status, String code, String key) { super(status, code, "error.photo-record." + key); }
    public static PhotoRecordException notFound() { return new PhotoRecordException(HttpStatus.NOT_FOUND, "PHOTO_RECORD_NOT_FOUND", "not-found"); }
    public static PhotoRecordException invalidMedia() { return new PhotoRecordException(HttpStatus.BAD_REQUEST, "PHOTO_RECORD_INVALID_MEDIA", "invalid-media"); }
}
