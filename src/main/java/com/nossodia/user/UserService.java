package com.nossodia.user;

import com.nossodia.shared.exception.ApiException;
import com.nossodia.user.dto.UserResponse;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
    private final UserRepository users;
    private final Clock clock;
    public UserService(UserRepository users, Clock clock) {
        this.users = users;
        this.clock = clock;
    }
    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
    @Transactional
    public UserResponse register(String name, String email, String passwordHash) {
        String normalized = normalizeEmail(email);
        var now = clock.instant();
        var user = new User(name.strip(), normalized, passwordHash, now);
        // An atomic conflict check also avoids driver/ORM error logs containing duplicate emails.
        if (users.insertIfEmailAvailable(user.getId(), user.getName(), normalized, passwordHash, now) == 0)
            throw duplicateEmail();
        return response(user);
    }
    @Transactional(readOnly = true)
    public Optional<User> findForAuthentication(String email) {
        return users.findByEmail(normalizeEmail(email));
    }
    @Transactional(readOnly = true)
    public String passwordHash(UUID id) {
        return users.findById(id).orElseThrow(UserService::unauthenticated).getPasswordHash();
    }
    @Transactional(readOnly = true)
    public UserResponse current(UUID id) {
        return response(users.findById(id).orElseThrow(UserService::unauthenticated));
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public User lock(UUID id) {
        return users.lockById(id).orElseThrow(UserService::unauthenticated);
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void changePassword(User user, String hash) {
        user.changePassword(hash, clock.instant());
    }
    private static UserResponse response(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }
    private static ApiException duplicateEmail() {
        return new UserError(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "error.auth.email-already-registered");
    }
    private static ApiException unauthenticated() {
        return new UserError(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "error.auth.unauthenticated");
    }
    private static class UserError extends ApiException {
        UserError(HttpStatus status, String code, String key) { super(status, code, key); }
    }
}
