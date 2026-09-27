package com.beehome.history.exception;

import com.beehome.shared.exception.ApiException;
import org.springframework.http.HttpStatus;

public final class ReportException extends ApiException {
    private ReportException(HttpStatus status, String code, String key) {
        super(status, code, key);
    }

    public static ReportException tooLarge() {
        return new ReportException(HttpStatus.UNPROCESSABLE_ENTITY, "REPORT_TOO_LARGE", "error.report.too-large");
    }

    public static ReportException busy() {
        return new ReportException(HttpStatus.SERVICE_UNAVAILABLE, "REPORT_BUSY", "error.report.busy");
    }
}
