package com.beehome.photorecord.controller;

import com.beehome.shared.exception.GlobalExceptionHandler;
import com.beehome.shared.exception.InputException;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = PhotoRecordController.class)
public class PhotoRecordInputExceptionHandler {
    private final GlobalExceptionHandler errors;
    public PhotoRecordInputExceptionHandler(GlobalExceptionHandler errors) { this.errors=errors; }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail malformedBody() { return errors.handleApplication(new InputException()); }
}
