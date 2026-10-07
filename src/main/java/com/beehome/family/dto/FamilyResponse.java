package com.beehome.family.dto;

import com.beehome.family.entity.FamilyRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"id", "name", "myRole", "createdAt", "updatedAt"})
public record FamilyResponse(UUID id, String name, FamilyRole myRole, Instant createdAt, Instant updatedAt) {}
