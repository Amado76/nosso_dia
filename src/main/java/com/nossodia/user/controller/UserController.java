package com.nossodia.user.controller;

import com.nossodia.user.service.UserService;

import com.nossodia.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@SecurityRequirement(name = "bearerAuth")
public class UserController {
    private final UserService users;
    public UserController(UserService users) { this.users = users; }
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt principal) {
        return users.current(UUID.fromString(principal.getSubject()));
    }
}
