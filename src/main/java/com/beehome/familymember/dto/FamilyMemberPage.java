package com.beehome.familymember.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"items", "page", "size", "hasNext"})
public record FamilyMemberPage(List<FamilyMemberResponse> items, int page, int size, boolean hasNext) {}
