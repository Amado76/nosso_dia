package com.nossodia.family.dto;

import com.nossodia.family.entity.FamilyRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"id", "name", "timezone", "myRole", "createdAt", "updatedAt"})
public record FamilyResponse(UUID id, String name, String timezone, FamilyRole myRole, Instant createdAt, Instant updatedAt) {}
