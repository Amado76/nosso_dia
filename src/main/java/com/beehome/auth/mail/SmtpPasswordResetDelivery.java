package com.beehome.auth.mail;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

final class SmtpPasswordResetDelivery implements PasswordResetDelivery {
    private static final Logger LOG = LoggerFactory.getLogger(SmtpPasswordResetDelivery.class);
    private final JavaMailSender sender;
    private final Executor executor;
    private final ResetMailProperties properties;
    private final Duration lifetime;

    SmtpPasswordResetDelivery(JavaMailSender sender, Executor executor, ResetMailProperties properties, Duration lifetime) {
        this.sender = sender;
        this.executor = executor;
        this.properties = properties;
        this.lifetime = lifetime;
    }

    @Override
    public void send(String email, String rawToken) {
        try {
            executor.execute(() -> {
                try {
                    var message = new SimpleMailMessage();
                    message.setFrom(properties.from());
                    message.setTo(email);
                    message.setSubject("Reset your BeeHome password");
                    String link = properties.resetUrl().toASCIIString() + "?token="
                            + java.net.URLEncoder.encode(rawToken, java.nio.charset.StandardCharsets.UTF_8);
                    message.setText("""
                            A password reset was requested for your BeeHome account.

                            Open this link to choose a new password:
                            %s

                            This link expires %s after the request and can only be used once.
                            If you did not request this, you can ignore this email.
                            """.formatted(link, lifetime.toSeconds() % 60 == 0
                                    ? lifetime.toMinutes() + " minutes" : lifetime.toSeconds() + " seconds"));
                    sender.send(message);
                } catch (RuntimeException exception) {
                    // Provider exceptions can contain addresses, credentials and message contents.
                    LOG.warn("Password reset email delivery failed");
                }
            });
        } catch (RejectedExecutionException exception) {
            LOG.warn("Password reset email queue is full or shutting down");
        }
    }
}
