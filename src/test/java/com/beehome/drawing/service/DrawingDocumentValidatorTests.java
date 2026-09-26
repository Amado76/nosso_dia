package com.beehome.drawing.service;

import com.beehome.drawing.exception.DrawingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DrawingDocumentValidatorTests {
    private final String stroke = """
            {"id":"8e9bb12d-8296-45a4-9c91-d17ddae12be3","color":"#202124","width":3,"points":[{"x":0,"y":1}]}
            """.strip();

    @Test
    void enforcesConfiguredStrokeAndPointCounts() {
        var validator = new DrawingDocumentValidator(1, 1, 1024, new ObjectMapper());
        String another = stroke.replace("8e9bb12d-8296-45a4-9c91-d17ddae12be3", "4ec3f4e2-ae79-40dd-813b-58ff57bb668d");
        assertThatThrownBy(() -> validator.parse(bytes("{\"formatVersion\":1,\"revision\":0,\"strokes\":[" + stroke + "," + another + "]}")))
                .isInstanceOf(DrawingException.class).hasFieldOrPropertyWithValue("code", "DRAWING_TOO_LARGE");
        String twoPoints = stroke.replace("{\"x\":0,\"y\":1}", "{\"x\":0,\"y\":1},{\"x\":1,\"y\":0}");
        assertThatThrownBy(() -> validator.parse(bytes("{\"formatVersion\":1,\"revision\":0,\"strokes\":[" + twoPoints + "]}")))
                .isInstanceOf(DrawingException.class).hasFieldOrPropertyWithValue("code", "DRAWING_TOO_LARGE");
    }

    @Test
    void rejectsUnknownNestedFieldsAndMalformedJson() {
        var validator = new DrawingDocumentValidator(1, 1, 1024, new ObjectMapper());
        String unknown = stroke.replace("\"width\":3", "\"width\":3,\"tool\":\"pen\"");
        assertThatThrownBy(() -> validator.parse(bytes("{\"formatVersion\":1,\"revision\":0,\"strokes\":[" + unknown + "]}")))
                .isInstanceOf(DrawingException.class).hasFieldOrPropertyWithValue("code", "DRAWING_INVALID_STROKE");
        assertThatThrownBy(() -> validator.parse(bytes("{\"formatVersion\":1,")))
                .isInstanceOf(DrawingException.class).hasFieldOrPropertyWithValue("code", "DRAWING_INVALID_FORMAT");
        assertThatThrownBy(() -> validator.parse(bytes("{\"formatVersion\":1,\"revision\":0,\"strokes\":[],\"revision\":0}")))
                .isInstanceOf(DrawingException.class).hasFieldOrPropertyWithValue("code", "DRAWING_INVALID_FORMAT");
        assertThatThrownBy(() -> validator.parse(bytes("{\"formatVersion\":1,\"revision\":0,\"strokes\":[]} {}")))
                .isInstanceOf(DrawingException.class).hasFieldOrPropertyWithValue("code", "DRAWING_INVALID_FORMAT");
    }

    private byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
