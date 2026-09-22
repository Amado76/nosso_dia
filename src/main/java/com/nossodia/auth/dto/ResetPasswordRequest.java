package com.nossodia.auth.dto;

import jakarta.validation.constraints.*;

public record ResetPasswordRequest(
        @NotBlank(message = "{validation.required}") @Size(max = 256, message = "{validation.auth.token}") String token,
        @ValidPassword String newPassword) {
    @Override public String toString() { return "ResetPasswordRequest[redacted]"; }
}
