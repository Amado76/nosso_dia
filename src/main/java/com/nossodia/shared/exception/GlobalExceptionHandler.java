package com.nossodia.shared.exception;

import com.nossodia.shared.localization.LocalizationService;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final LocalizationService localization;

    public GlobalExceptionHandler(LocalizationService localization) {
        this.localization = localization;
    }

    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApplication(ApiException exception) {
        return problem(exception.getStatus(), exception.getCode(),
                localization.get(exception.getMessageKey(), exception.getArguments()));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        var errors = exception.getBindingResult().getAllErrors().stream()
                .map(error -> new ValidationError(
                        error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName(),
                        error.getDefaultMessage()))
                .toList();
        return validationResponse(exception, errors, headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        if (exception.isForReturnValue()) {
            return handleExceptionInternal(exception, handleUnexpected(exception), headers,
                    HttpStatus.INTERNAL_SERVER_ERROR, request);
        }
        var errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ValidationError(
                                error instanceof FieldError fieldError ? fieldError.getField()
                                        : result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return validationResponse(exception, errors, headers, request);
    }

    private ResponseEntity<Object> validationResponse(
            Exception exception, List<ValidationError> errors, HttpHeaders headers, WebRequest request) {
        var problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", localization.get("error.validation"));
        problem.setTitle(localization.get("error.validation"));
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        // Security exceptions must reach the filter chain's authentication/authorization handlers.
        if (exception instanceof AccessDeniedException denied) {
            throw denied;
        }
        if (exception instanceof AuthenticationException authentication) {
            throw authentication;
        }
        // Exception messages and causes can contain credentials, SQL, or personal data.
        var safeTrace = new Throwable("Exception details omitted to protect sensitive data");
        safeTrace.setStackTrace(exception.getStackTrace());
        log.error("Unexpected error while processing request ({})", exception.getClass().getName(), safeTrace);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", localization.get("error.internal"));
    }

    private ProblemDetail problem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setProperty("code", code);
        return problem;
    }
}
