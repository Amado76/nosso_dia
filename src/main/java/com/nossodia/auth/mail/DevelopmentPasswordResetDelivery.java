package com.nossodia.auth.mail;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/** Deliberately discards delivery in local development; tests replace this boundary. */
@Component
@ConditionalOnExpression("${auth.allow-ephemeral-key:false} && !${auth.reset-mail.enabled:false}")
class DevelopmentPasswordResetDelivery implements PasswordResetDelivery {
    @Override public void send(String email, String rawToken) {
        // No console mailbox: reset secrets must never reach application logs.
    }
}
