package com.nossodia.auth.mail;

import com.nossodia.auth.security.AuthProperties;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class ResetMailTests {
    private final ResetMailProperties properties =
            new ResetMailProperties("no-reply@example.com", URI.create("https://app.example.com/reset"));

    @Test
    void refusesEnabledMailWithoutASmtpHost() {
        new ApplicationContextRunner().withUserConfiguration(ResetMailConfiguration.class)
                .withBean(JavaMailSender.class, () -> mock(JavaMailSender.class))
                .withBean(AuthProperties.class, () -> new AuthProperties(null, true, "nosso-dia",
                        Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofMinutes(30),
                        Duration.ofMinutes(5), false, 30))
                .withPropertyValues("auth.reset-mail.enabled=true", "auth.reset-mail.from=no-reply@example.com",
                        "auth.reset-mail.reset-url=https://app.example.com/reset", "spring.mail.host=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsUnsafeLinksAndSenderHeaders() {
        for (String url : new String[]{"http://example.com/reset", "/reset", "https://user:secret@example.com/reset",
                "https://example.com/reset?redirect=other", "https://example.com/reset#fragment"}) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    new ResetMailProperties("no-reply@example.com", URI.create(url)));
        }
        assertThatIllegalArgumentException().isThrownBy(() ->
                new ResetMailProperties("from@example.com\r\nBcc: other@example.com", properties.resetUrl()));
        assertThatCode(() -> new ResetMailProperties("from@example.com", URI.create("http://localhost:3000/reset")))
                .doesNotThrowAnyException();
    }

    @Test
    void providerFailureDoesNotExposeMailContents(CapturedOutput output) {
        var sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("private@example.com reset-secret smtp-password"))
                .when(sender).send(any(SimpleMailMessage.class));
        var delivery = new SmtpPasswordResetDelivery(sender, Runnable::run, properties, Duration.ofMinutes(30));
        assertThatCode(() -> delivery.send("private@example.com", "reset-secret")).doesNotThrowAnyException();
        assertThat(output.getAll()).contains("Password reset email delivery failed")
                .doesNotContain("private@example.com", "reset-secret", "smtp-password");
    }

    @Test
    void slowProviderUsesBoundedQueueWithoutBlockingTheCaller(CapturedOutput output) throws Exception {
        var sender = mock(JavaMailSender.class);
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var completed = new CountDownLatch(102);
        doAnswer(invocation -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Test did not release SMTP");
            completed.countDown();
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
        var executor = new ResetMailConfiguration().passwordResetMailExecutor();
        executor.initialize();
        try {
            var delivery = new SmtpPasswordResetDelivery(sender, executor, properties, Duration.ofMinutes(30));
            delivery.send("private@example.com", "secret");
            delivery.send("private@example.com", "secret");
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 101; i++) delivery.send("private@example.com", "secret");
            assertThat(output.getAll()).contains("Password reset email queue is full")
                    .doesNotContain("private@example.com", "secret");
            release.countDown();
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();
            verify(sender, times(102)).send(any(SimpleMailMessage.class));
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }
}
