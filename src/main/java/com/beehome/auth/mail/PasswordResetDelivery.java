package com.beehome.auth.mail;

/** Notification boundary: implementations must never log the recipient or reset secret. */
public interface PasswordResetDelivery {
    void send(String email, String rawToken);
}
