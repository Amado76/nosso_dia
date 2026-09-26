package com.beehome.drawing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import java.util.List;
import java.util.UUID;

@Schema(requiredProperties = {"id", "color", "width", "points"})
public record DrawingStroke(UUID id,
        @Schema(pattern = "^#[0-9A-Fa-f]{6}$", example = "#202124") String color,
        @Schema(exclusiveMinimum = true, minimum = "0", maximum = "100") double width,
        @ArraySchema(minItems = 1, maxItems = 10000, schema = @Schema(implementation = DrawingPoint.class)) List<DrawingPoint> points) {}
