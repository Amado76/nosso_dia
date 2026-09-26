package com.beehome.tag.dto;

import com.beehome.tag.entity.Tag;
import java.util.UUID;

public record TagSummary(UUID id, String name, String color) {
    public static TagSummary from(Tag tag) { return new TagSummary(tag.getId(), tag.getName(), tag.getColor()); }
}
