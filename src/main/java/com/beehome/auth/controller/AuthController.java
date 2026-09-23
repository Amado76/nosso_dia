package com.beehome.auth.controller;

import com.beehome.auth.service.AuthService;
import com.beehome.auth.service.PasswordResetService;
import com.beehome.auth.service.TokenService;

import com.beehome.auth.dto.*;
import com.beehome.shared.localization.LocalizationService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import com.beehome.user.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final TokenService tokens;
    private final PasswordResetService resets;
    private final LocalizationService localization;
    public AuthController(AuthService auth, TokenService tokens, PasswordResetService resets, LocalizationService localization) {
        this.auth = auth; this.tokens = tokens; this.resets = resets; this.localization = localization;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) { return auth.login(request); }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return tokens.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @SecurityRequirement(name = "bearerAuth")
    public void logout(@AuthenticationPrincipal Jwt principal, @Valid @RequestBody RefreshTokenRequest request) {
        tokens.logout(UUID.fromString(principal.getSubject()), request.refreshToken());
    }

    @PostMapping("/forgot-password")
    public MessageResponse forgot(@Valid @RequestBody ForgotPasswordRequest request) {
        resets.forgot(request.email());
        return new MessageResponse(localization.get("success.auth.password-reset-requested"));
    }

    @PostMapping("/reset-password")
    public MessageResponse reset(@Valid @RequestBody ResetPasswordRequest request) {
        resets.reset(request.token(), request.newPassword());
        return new MessageResponse(localization.get("success.auth.password-reset"));
    }
    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    public MessageResponse changePassword(@AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        resets.change(UUID.fromString(principal.getSubject()), request.currentPassword(), request.newPassword());
        return new MessageResponse(localization.get("success.auth.password-changed"));
    }
}
