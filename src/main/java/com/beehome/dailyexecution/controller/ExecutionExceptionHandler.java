package com.beehome.dailyexecution.controller;

import com.beehome.dailyexecution.exception.DailyExecutionException;
import com.beehome.shared.exception.GlobalExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = DailyExecutionController.class)
public class ExecutionExceptionHandler {
    private final GlobalExceptionHandler errors;
    public ExecutionExceptionHandler(GlobalExceptionHandler errors) { this.errors = errors; }

    @ExceptionHandler({PessimisticLockingFailureException.class, QueryTimeoutException.class})
    public ProblemDetail conflict() {
        return errors.handleApplication(DailyExecutionException.conflict());
    }
}
