package com.beehome.drawing.service;

import com.beehome.drawing.dto.*;
import com.beehome.drawing.exception.DrawingException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class DrawingDocumentValidator {
    private final int maxStrokes;
    private final int maxPointsPerStroke;
    private final int maxDocumentBytes;
    private final ObjectMapper mapper;

    public DrawingDocumentValidator(@Value("${app.drawings.max-strokes:5000}") int maxStrokes,
            @Value("${app.drawings.max-points-per-stroke:10000}") int maxPointsPerStroke,
            @Value("${app.drawings.max-document-bytes:5242880}") int maxDocumentBytes, ObjectMapper mapper) {
        this.maxStrokes = maxStrokes;
        this.maxPointsPerStroke = maxPointsPerStroke;
        this.maxDocumentBytes = maxDocumentBytes;
        this.mapper = mapper;
    }

    public DrawingUpdate parse(byte[] body) {
        if (body.length > maxDocumentBytes) throw DrawingException.tooLarge();
        Map<String, Object> input;
        try {
            input = mapper.readerFor(Map.class)
                    .with(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(body);
        }
        catch (JacksonException exception) { throw DrawingException.format(); }
        return parseDocument(input);
    }

    private DrawingUpdate parseDocument(Map<String, Object> input) {
        if (!fields(input, Set.of("formatVersion", "revision", "strokes"))) throw DrawingException.format();
        Object version = input.get("formatVersion"), revision = input.get("revision"), strokes = input.get("strokes");
        if (!integral(version)) throw DrawingException.format();
        if (!integer(version).equals(BigInteger.ONE)) throw DrawingException.version();
        if (!integral(revision) || integer(revision).signum() < 0
                || integer(revision).compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 || !(strokes instanceof List<?> list)) {
            throw DrawingException.format();
        }
        if (list.size() > maxStrokes) throw DrawingException.tooLarge();
        var parsed = new ArrayList<DrawingStroke>(list.size());
        var ids = new HashSet<UUID>();
        for (Object item : list) {
            if (!fields(item, Set.of("id", "color", "width", "points"))) throw DrawingException.stroke();
            Map<?, ?> stroke = (Map<?, ?>) item;
            Object id = stroke.get("id"), color = stroke.get("color"), width = stroke.get("width"), points = stroke.get("points");
            if (!(id instanceof String text) || !(color instanceof String hex) || !hex.matches("#[0-9A-Fa-f]{6}")
                    || !bounded(width, BigDecimal.ZERO, BigDecimal.valueOf(100), false)
                    || !(points instanceof List<?> pointList) || pointList.isEmpty()) throw DrawingException.stroke();
            UUID strokeId;
            try {
                strokeId = UUID.fromString(text);
                if (!strokeId.toString().equalsIgnoreCase(text)) throw DrawingException.stroke();
            } catch (IllegalArgumentException exception) { throw DrawingException.stroke(); }
            if (!ids.add(strokeId)) throw DrawingException.stroke();
            if (pointList.size() > maxPointsPerStroke) throw DrawingException.tooLarge();
            var parsedPoints = new ArrayList<DrawingPoint>(pointList.size());
            for (Object value : pointList) {
                if (!fields(value, Set.of("x", "y", "pressure"))) throw DrawingException.point();
                Map<?, ?> point = (Map<?, ?>) value;
                if (!bounded(point.get("x"), BigDecimal.ZERO, BigDecimal.ONE, true)
                        || !bounded(point.get("y"), BigDecimal.ZERO, BigDecimal.ONE, true)
                        || (point.containsKey("pressure") && !bounded(point.get("pressure"), BigDecimal.ZERO, BigDecimal.ONE, true))) {
                    throw DrawingException.point();
                }
                parsedPoints.add(new DrawingPoint(number(point.get("x")).doubleValue(), number(point.get("y")).doubleValue(),
                        point.containsKey("pressure") ? number(point.get("pressure")).doubleValue() : null));
            }
            parsed.add(new DrawingStroke(strokeId, hex, number(width).doubleValue(), List.copyOf(parsedPoints)));
        }
        var update = new DrawingUpdate(1, integer(revision).longValueExact(), List.copyOf(parsed));
        if (mapper.writeValueAsBytes(new DrawingDocument(1, update.strokes())).length > maxDocumentBytes) {
            throw DrawingException.tooLarge();
        }
        return update;
    }

    private boolean fields(Object value, Set<String> allowed) {
        if (!(value instanceof Map<?, ?> map)) return false;
        return map.keySet().stream().allMatch(allowed::contains);
    }

    private boolean integral(Object value) {
        return value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof BigInteger;
    }

    private BigInteger integer(Object value) { return new BigInteger(value.toString()); }
    private BigDecimal number(Object value) { return new BigDecimal(value.toString()); }

    private boolean bounded(Object value, BigDecimal min, BigDecimal max, boolean inclusiveMin) {
        if (!(value instanceof Number)) return false;
        BigDecimal decimal;
        try { decimal = number(value); }
        catch (NumberFormatException exception) { return false; }
        return (inclusiveMin ? decimal.compareTo(min) >= 0 : decimal.compareTo(min) > 0) && decimal.compareTo(max) <= 0;
    }
}
