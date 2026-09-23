package com.beehome.familymember.dto;

import com.beehome.familymember.entity.MemberType;
import java.time.LocalDate;

final class MemberJson {
    private MemberJson() {}

    static String string(Object value) {
        if (value == null) return null;
        if (value instanceof String text) return text;
        throw new IllegalArgumentException("Expected a string");
    }

    static MemberType type(Object value) {
        String text = string(value);
        return text == null ? null : MemberType.valueOf(text);
    }

    static LocalDate date(Object value) {
        String text = string(value);
        if (text == null) return null;
        if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("Expected an ISO calendar date");
        return LocalDate.parse(text);
    }
}
