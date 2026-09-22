package com.nossodia.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "{validation.required}")
        @Size(max = 128, message = "{validation.auth.password-length}") String currentPassword,
        @ValidPassword String newPassword) {
    @Override public String toString() { return "ChangePasswordRequest[redacted]"; }
}
