package com.beehome.drawing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"formatVersion", "strokes"})
public record DrawingDocument(@Schema(allowableValues = "1") int formatVersion,
        List<DrawingStroke> strokes) {}
