package com.nossodia;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"auth.allow-ephemeral-key=true", "auth.requests-per-minute=10000",
        "auth.reset-mail.enabled=true", "auth.reset-mail.from=no-reply@example.com",
        "auth.reset-mail.reset-url=https://app.example.com/reset-password",
        "spring.mail.properties.mail.smtp.auth=false",
        "spring.mail.properties.mail.smtp.starttls.enable=false",
        "spring.mail.properties.mail.smtp.starttls.required=false"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PasswordResetMailTests {
    @Autowired MockMvc mvc;
    private static final ServerSocket SMTP = smtpServer();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Future<String> MESSAGE = WORKER.submit(PasswordResetMailTests::receive);

    private static ServerSocket smtpServer() {
        try { return new ServerSocket(0, 1, InetAddress.getLoopbackAddress()); }
        catch (IOException exception) { throw new UncheckedIOException(exception); }
    }

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> SMTP.getInetAddress().getHostAddress());
        registry.add("spring.mail.port", SMTP::getLocalPort);
    }

    @AfterAll
    static void close() throws IOException {
        SMTP.close();
        WORKER.shutdownNow();
    }

    @Test
    void recoverySendsAnEmailWithAUsableSingleUseResetLink() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"name\":\"Alex\",\"email\":\"" + email + "\",\"password\":\"Secure-password1!\"}"))
                .andExpect(status().isCreated());
        String response = mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String message = MESSAGE.get(5, TimeUnit.SECONDS);
        assertThat(message).contains("From: no-reply@example.com", "To: " + email,
                "Subject: Reset your Nosso Dia password", "30 minutes");
        var token = java.util.regex.Pattern.compile("https://app.example.com/reset-password\\?token=([A-Za-z0-9_-]+)")
                .matcher(message);
        assertThat(token.find()).isTrue();
        assertThat(response).doesNotContain(token.group(1));
        String body = "{\"token\":\"" + token.group(1) + "\",\"newPassword\":\"Changed-password2!\"}";
        mvc.perform(post("/api/auth/reset-password").contentType("application/json").content(body))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/reset-password").contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"Changed-password2!\"}"))
                .andExpect(status().isOk());
    }

    // A loopback SMTP receiver verifies the actual transport without contacting a provider.
    private static String receive() throws IOException {
        try (Socket socket = SMTP.accept();
                var reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                var writer = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8)) {
            socket.setSoTimeout(10000);
            writer.print("220 localhost\r\n"); writer.flush();
            var message = new StringBuilder();
            boolean data = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (data && !line.equals(".")) { message.append(line).append('\n'); continue; }
                if (data) {
                    data = false;
                    writer.print("250 Accepted\r\n");
                } else if (line.equals("DATA")) {
                    data = true;
                    writer.print("354 Send message\r\n");
                } else if (line.equals("QUIT")) {
                    writer.print("221 Bye\r\n"); writer.flush();
                    return message.toString();
                } else writer.print("250 OK\r\n");
                writer.flush();
            }
            return message.toString();
        }
    }
}
