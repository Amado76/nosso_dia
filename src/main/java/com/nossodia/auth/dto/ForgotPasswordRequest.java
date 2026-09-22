package com.nossodia.auth.dto;

import jakarta.validation.constraints.*;

public record ForgotPasswordRequest(
        @NotBlank(message = "{validation.required}") @Email(message = "{validation.auth.email}")
        @Size(max = 254, message = "{validation.auth.email}") String email) {
    public ForgotPasswordRequest { if (email != null) email = email.strip(); }
    @Override public String toString() { return "ForgotPasswordRequest[redacted]"; }
}
