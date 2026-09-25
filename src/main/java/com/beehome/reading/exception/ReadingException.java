package com.beehome.reading.exception;
import com.beehome.shared.exception.ApiException;
import org.springframework.http.HttpStatus;
public class ReadingException extends ApiException {
    private ReadingException(HttpStatus status, String code) { super(status, code, "error.reading." + code.toLowerCase(java.util.Locale.ROOT)); }
    public static ReadingException missingBook() { return new ReadingException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND"); }
    public static ReadingException missingJourney() { return new ReadingException(HttpStatus.NOT_FOUND, "CHILD_BOOK_NOT_FOUND"); }
    public static ReadingException missingSession() { return new ReadingException(HttpStatus.NOT_FOUND, "READING_SESSION_NOT_FOUND"); }
    public static ReadingException active() { return new ReadingException(HttpStatus.CONFLICT, "BOOK_ALREADY_ACTIVE"); }
    public static ReadingException inUse() { return new ReadingException(HttpStatus.CONFLICT, "BOOK_IN_USE"); }
    public static ReadingException invalid(String code) { return new ReadingException(HttpStatus.BAD_REQUEST, code); }
}
