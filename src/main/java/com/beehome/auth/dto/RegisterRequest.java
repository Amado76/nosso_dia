package com.beehome.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "{validation.required}") @Size(max = 120, message = "{validation.auth.name}") String name,
        @NotBlank(message = "{validation.required}") @Email(message = "{validation.auth.email}")
        @Size(max = 254, message = "{validation.auth.email}") String email,
        @ValidPassword String password) {
    public RegisterRequest { if (email != null) email = email.strip(); }
    @Override public String toString() { return "RegisterRequest[redacted]"; }
}
