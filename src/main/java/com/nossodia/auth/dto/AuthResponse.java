package com.nossodia.auth.dto;

public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresIn) {
    @Override public String toString() { return "AuthResponse[redacted]"; }
}
