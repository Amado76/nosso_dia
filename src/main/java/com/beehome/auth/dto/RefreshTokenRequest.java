package com.beehome.auth.dto;

import jakarta.validation.constraints.*;

public record RefreshTokenRequest(
        @NotBlank(message = "{validation.required}") @Size(max = 256, message = "{validation.auth.token}") String refreshToken) {
    @Override public String toString() { return "RefreshTokenRequest[redacted]"; }
}
