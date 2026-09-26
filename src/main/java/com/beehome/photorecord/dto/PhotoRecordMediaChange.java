package com.beehome.photorecord.dto;

import java.util.UUID;
import com.beehome.shared.exception.InputException;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties="mediaId")
public record PhotoRecordMediaChange(UUID mediaId,
        @Schema(description="Zero-based insertion position; omitted to append", nullable=true, minimum="0") Integer position) {
    public void validate() { if (mediaId==null) throw new InputException(); }
}
