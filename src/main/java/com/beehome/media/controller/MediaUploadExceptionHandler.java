package com.beehome.media.controller;

import com.beehome.media.exception.MediaException;
import com.beehome.shared.exception.GlobalExceptionHandler;
import com.beehome.shared.exception.InputException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Order(0)
// Multipart parsing may fail before Spring selects a controller.
@RestControllerAdvice
public class MediaUploadExceptionHandler {
    private final GlobalExceptionHandler errors;
    public MediaUploadExceptionHandler(GlobalExceptionHandler errors) { this.errors = errors; }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge() { return errors.handleApplication(MediaException.tooLarge()); }
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail missingFile() { return errors.handleApplication(new InputException()); }
}
