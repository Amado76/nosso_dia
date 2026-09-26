package com.beehome.photorecord.dto;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties="mediaIds")
public record PhotoRecordMediaOrder(
        @ArraySchema(minItems=1,maxItems=20,schema=@Schema(description="Unique image media ID in display order"))
        List<UUID> mediaIds) {}
