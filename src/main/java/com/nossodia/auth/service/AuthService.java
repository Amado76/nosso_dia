package com.nossodia.auth.service;

import com.nossodia.auth.exception.AuthException;
import com.nossodia.auth.security.TokenSecrets;

import com.nossodia.auth.dto.RegisterRequest;
import com.nossodia.user.UserService;
import com.nossodia.user.dto.UserResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserService users;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final org.springframework.transaction.support.TransactionTemplate transactions;
    private final String dummyHash;
    public AuthService(UserService users, PasswordEncoder passwords, TokenService tokens,
            org.springframework.transaction.PlatformTransactionManager manager) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.transactions = new org.springframework.transaction.support.TransactionTemplate(manager);
        this.dummyHash = passwords.encode(TokenSecrets.generate());
    }
    public UserResponse register(RegisterRequest request) {
        return users.register(request.name(), request.email(), passwords.encode(request.password()));
    }

    public com.nossodia.auth.dto.AuthResponse login(com.nossodia.auth.dto.LoginRequest request) {
        var found = users.findForAuthentication(request.email());
        String hash = found.map(com.nossodia.user.User::getPasswordHash).orElse(dummyHash);
        boolean matches = passwords.matches(request.password(), hash);
        if (!matches || found.isEmpty() || found.get().getPasswordHash() == null) throw invalidCredentials();
        return transactions.execute(status -> {
            var user = users.lock(found.get().getId());
            // A reset committed during password verification must not create a new session.
            if (!hash.equals(user.getPasswordHash())) throw invalidCredentials();
            return tokens.issue(user.getId());
        });
    }

    private static AuthException invalidCredentials() {
        return new AuthException("INVALID_CREDENTIALS", "error.auth.invalid-credentials");
    }
}
