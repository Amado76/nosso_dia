package com.beehome.study.exception;

import com.beehome.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public class StudyException extends ApiException {
    private StudyException(HttpStatus status, String code, String key) { super(status, code, "error.study." + key); }
    public static StudyException subjectMissing() { return new StudyException(HttpStatus.NOT_FOUND, "STUDY_SUBJECT_NOT_FOUND", "subject-not-found"); }
    public static StudyException sessionMissing() { return new StudyException(HttpStatus.NOT_FOUND, "STUDY_SESSION_NOT_FOUND", "session-not-found"); }
    public static StudyException itemMissing() { return new StudyException(HttpStatus.NOT_FOUND, "STUDY_EXECUTION_ITEM_NOT_FOUND", "item-not-found"); }
    public static StudyException duplicate() { return new StudyException(HttpStatus.CONFLICT, "STUDY_SUBJECT_DUPLICATE", "duplicate"); }
    public static StudyException state() { return new StudyException(HttpStatus.CONFLICT, "STUDY_INVALID_STATE", "state"); }
    public static StudyException conflict() { return new StudyException(HttpStatus.CONFLICT, "STUDY_CONFLICT", "conflict"); }
}
