package com.beehome.drawing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import java.util.List;

@Schema(requiredProperties = {"formatVersion", "revision", "strokes"})
public record DrawingUpdate(@Schema(allowableValues = "1") int formatVersion,
        @Schema(minimum = "0") long revision,
        @ArraySchema(maxItems = 5000, schema = @Schema(implementation = DrawingStroke.class)) List<DrawingStroke> strokes) {}
