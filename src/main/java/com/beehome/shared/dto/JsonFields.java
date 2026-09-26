package com.beehome.shared.dto;

import com.beehome.shared.exception.InputException;
import java.time.*;
import java.util.*;

/** Strict scalar parsing shared by presence-aware request DTOs. */
public final class JsonFields {
    private JsonFields() {}
    public static void only(Map<String, Object> values, String... allowed) {
        if (values == null || !Set.of(allowed).containsAll(values.keySet())) throw new InputException();
    }
    public static String string(Object value) {
        if (value == null) return null;
        if (value instanceof String text) return text;
        throw new InputException();
    }
    public static String text(String value, int max, boolean required) {
        String result = value == null ? null : value.strip();
        if (result != null && result.isBlank()) result = null;
        if ((required && result == null) || (result != null && result.length() > max)) throw new InputException();
        return result;
    }
    public static UUID uuid(Object value) {
        if (value == null) return null;
        try {
            String text = string(value);
            UUID id = UUID.fromString(text);
            if (!id.toString().equalsIgnoreCase(text)) throw new InputException();
            return id;
        } catch (IllegalArgumentException e) { throw new InputException(); }
    }
    public static List<UUID> uuidList(Map<String,Object> values, String field) {
        if (!values.containsKey(field)) return List.of();
        if (!(values.get(field) instanceof List<?> raw) || raw.size() > 100) throw new InputException();
        return raw.stream().map(JsonFields::uuid).toList();
    }
    public static Integer integer(Object value) {
        if (value == null) return null;
        if (value instanceof Integer n && n >= 0) return n;
        throw new InputException();
    }
    public static LocalDate date(Object value) {
        if (value == null) return null;
        String text = string(value);
        try {
            if (!text.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new InputException();
            return LocalDate.parse(text);
        } catch (DateTimeException e) { throw new InputException(); }
    }
    public static LocalTime time(Object value) {
        if (value == null) return null;
        String text = string(value);
        try {
            if (!text.matches("[0-9]{2}:[0-9]{2}")) throw new InputException();
            return LocalTime.parse(text);
        } catch (DateTimeException e) { throw new InputException(); }
    }
    public static void required(Object value) { if (value == null) throw new InputException(); }
}
