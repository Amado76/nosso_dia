package com.beehome.family.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"items", "page", "size", "hasNext"})
public record FamilyPage(List<FamilyResponse> items, int page, int size, boolean hasNext) {}
