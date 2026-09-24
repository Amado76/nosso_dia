package com.beehome.study.controller;

import com.beehome.shared.exception.GlobalExceptionHandler;
import com.beehome.study.exception.StudyException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;

@Order(0)
@RestControllerAdvice(assignableTypes = {StudySubjectController.class, StudySessionController.class})
public class StudyExceptionHandler {
    private final GlobalExceptionHandler errors;
    public StudyExceptionHandler(GlobalExceptionHandler errors) { this.errors = errors; }
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail conflict() { return errors.handleApplication(StudyException.conflict()); }
}
