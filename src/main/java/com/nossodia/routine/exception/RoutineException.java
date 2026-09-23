package com.nossodia.routine.exception;
import com.nossodia.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class RoutineException extends ApiException {
    private RoutineException(String code, String key) { super(HttpStatus.NOT_FOUND, code, key); }
    public static RoutineException notFound() { return new RoutineException("ROUTINE_NOT_FOUND", "error.routine.not-found"); }
    public static RoutineException itemNotFound() { return new RoutineException("ROUTINE_ITEM_NOT_FOUND", "error.routine.item-not-found"); }
}
