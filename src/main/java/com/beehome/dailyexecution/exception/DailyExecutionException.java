package com.beehome.dailyexecution.exception;
import com.beehome.shared.exception.ApiException;
import org.springframework.http.HttpStatus;
public class DailyExecutionException extends ApiException {
    protected DailyExecutionException(HttpStatus status, String suffix, String key) {
        super(status, "DAILY_EXECUTION_" + suffix, "error.execution." + key);
    }
    public static DailyExecutionException missing() { return new DailyExecutionException(HttpStatus.NOT_FOUND, "NOT_FOUND", "not-found"); }
    public static DailyExecutionException itemMissing() { return new DailyExecutionException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "item-not-found"); }
    public static DailyExecutionException future() { return new DailyExecutionException(HttpStatus.BAD_REQUEST, "FUTURE_DATE", "future"); }
    public static DailyExecutionException conflict() { return new DailyExecutionException(HttpStatus.CONFLICT, "CONFLICT", "conflict"); }
}
