package com.beehome.dailyexecution.exception;
import org.springframework.http.HttpStatus;
/** Allows the lazy closing transition to commit when the requested correction is rejected. */
public final class FinalizedExecutionException extends DailyExecutionException {
    public FinalizedExecutionException() { super(HttpStatus.CONFLICT, "FINALIZED", "finalized"); }
}
