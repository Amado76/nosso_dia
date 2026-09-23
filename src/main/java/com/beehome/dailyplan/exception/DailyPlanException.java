package com.beehome.dailyplan.exception;
import com.beehome.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class DailyPlanException extends ApiException {
    private DailyPlanException(String code, String key) { super(HttpStatus.NOT_FOUND, code, key); }
    public static DailyPlanException notFound() { return new DailyPlanException("DAILY_PLAN_NOT_FOUND", "error.dailyplan.not-found"); }
    public static DailyPlanException itemNotFound() { return new DailyPlanException("DAILY_PLAN_ITEM_NOT_FOUND", "error.dailyplan.item-not-found"); }
}
