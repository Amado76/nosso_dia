package com.beehome.auth.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @NotBlank(message = "{validation.required}") @Email(message = "{validation.auth.email}")
        @Size(max = 254, message = "{validation.auth.email}") String email,
        @NotBlank(message = "{validation.required}") @Size(max = 128, message = "{validation.auth.password-length}") String password) {
    public LoginRequest { if (email != null) email = email.strip(); }
    @Override public String toString() { return "LoginRequest[redacted]"; }
}
