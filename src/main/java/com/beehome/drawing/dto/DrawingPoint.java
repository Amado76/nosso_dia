package com.beehome.drawing.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(requiredProperties = {"x", "y"})
public record DrawingPoint(@Schema(minimum = "0", maximum = "1") double x,
        @Schema(minimum = "0", maximum = "1") double y,
        @Schema(nullable = true, minimum = "0", maximum = "1") Double pressure) {}
