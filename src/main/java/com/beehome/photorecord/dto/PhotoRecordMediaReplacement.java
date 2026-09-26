package com.beehome.photorecord.dto;

import com.beehome.shared.dto.JsonFields;
import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties="mediaId")
public record PhotoRecordMediaReplacement(UUID mediaId) {
    public static PhotoRecordMediaReplacement from(Map<String,Object> values) {
        JsonFields.only(values,"mediaId");
        UUID mediaId = JsonFields.uuid(values.get("mediaId"));
        JsonFields.required(mediaId);
        return new PhotoRecordMediaReplacement(mediaId);
    }
}
