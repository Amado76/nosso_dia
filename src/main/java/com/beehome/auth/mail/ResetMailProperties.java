package com.beehome.auth.mail;

import java.net.URI;
import jakarta.mail.internet.InternetAddress;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("auth.reset-mail")
public record ResetMailProperties(String from, URI resetUrl) {
    public ResetMailProperties {
        try {
            if (from == null || from.contains("\r") || from.contains("\n")) throw new IllegalArgumentException();
            var address = new InternetAddress(from, true);
            address.validate();
            if (!from.equals(address.getAddress())) throw new IllegalArgumentException();
        } catch (Exception exception) {
            throw new IllegalArgumentException("AUTH_RESET_MAIL_FROM must be a single email address");
        }
        boolean localHttp = resetUrl != null && "http".equals(resetUrl.getScheme())
                && ("localhost".equals(resetUrl.getHost()) || "127.0.0.1".equals(resetUrl.getHost()));
        if (resetUrl == null || resetUrl.getHost() == null
                || !("https".equals(resetUrl.getScheme()) || localHttp)
                || resetUrl.getUserInfo() != null || resetUrl.getQuery() != null || resetUrl.getFragment() != null) {
            throw new IllegalArgumentException("AUTH_RESET_URL must be an HTTPS URL without credentials, query or fragment (HTTP is allowed on localhost)");
        }
    }
    @Override public String toString() { return "ResetMailProperties[redacted]"; }
}
