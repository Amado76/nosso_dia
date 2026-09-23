package com.beehome;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"auth.allow-ephemeral-key=true", "auth.requests-per-minute=10000"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthenticationTests {
    @Autowired MockMvc mvc;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @Autowired com.beehome.auth.service.ExternalIdentityService externalIdentities;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.beehome.auth.mail.PasswordResetDelivery delivery;

    private String register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"name\":\"Bruno\",\"email\":\"" + email + "\",\"password\":\"Secure-password1!\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }
    private String login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }
    private String field(String json, String name) { return com.jayway.jsonpath.JsonPath.read(json, "$." + name); }
    private org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"" + token + "\"}"));
    }
    private org.springframework.test.web.servlet.ResultActions reset(String token) throws Exception {
        return mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"new-Secure-password1!\"}"));
    }
    private String forgot(String email) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(String access, String current, String next) throws Exception {
        var request = post("/api/auth/change-password").contentType("application/json")
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}");
        if (access != null) request.header("Authorization", "Bearer " + access);
        return mvc.perform(request);
    }

    @Test
    void authenticatedPasswordChangePersistsAndInvalidatesSessionsAndResetLinks() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        register(email);
        String session = login(email, "Secure-password1!");
        String anotherSession = field(login(email, "Secure-password1!"), "refreshToken");
        String otherEmail = UUID.randomUUID() + "@example.com";
        register(otherEmail);
        String otherSession = field(login(otherEmail, "Secure-password1!"), "refreshToken");
        forgot(email);
        var captured = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(delivery).send(org.mockito.ArgumentMatchers.eq(email), captured.capture());

        changePassword(field(session, "accessToken"), "Secure-password1!", "Changed-password2!")
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Your password has been changed."));
        login(email, "Changed-password2!");
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"Secure-password1!\"}"))
                .andExpect(status().isUnauthorized());
        refresh(field(session, "refreshToken")).andExpect(status().isUnauthorized());
        refresh(anotherSession).andExpect(status().isUnauthorized());
        reset(captured.getValue()).andExpect(status().isBadRequest());
        refresh(otherSession).andExpect(status().isOk());
        login(otherEmail, "Secure-password1!");
    }

    @Test
    void passwordChangeRequiresAuthenticationCurrentPasswordAndValidNewPassword() throws Exception {
        changePassword(null, "Secure-password1!", "Changed-password2!").andExpect(status().isUnauthorized());
        String email = UUID.randomUUID() + "@example.com";
        register(email);
        String session = login(email, "Secure-password1!");
        String access = field(session, "accessToken");
        changePassword(access, "wrong", "Changed-password2!")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
        changePassword(access, "Secure-password1!", "weak")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        changePassword(access, "", "Changed-password2!")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        login(email, "Secure-password1!");
        refresh(field(session, "refreshToken")).andExpect(status().isOk());
    }

    @Test
    void normalizesEmailEnforcesUniquenessAndHashesPassword() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String registered = register("  " + email.toUpperCase(java.util.Locale.ROOT) + "  ");
        org.assertj.core.api.Assertions.assertThat(field(registered, "email")).isEqualTo(email);
        String hash = jdbc.queryForObject("select password_hash from beehome.users where email = ?", String.class, email);
        org.assertj.core.api.Assertions.assertThat(passwords.matches("Secure-password1!", hash)).isTrue();
        org.assertj.core.api.Assertions.assertThat(registered).doesNotContain(hash, "Secure-password1!", "password_hash");
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"name\":\"Another\",\"email\":\"" + email + "\",\"password\":\"Secure-password1!\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        login(email.toUpperCase(java.util.Locale.ROOT), "Secure-password1!");
    }

    @Test
    void unknownEmailAndWrongPasswordHaveEquivalentErrors() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        register(email);
        String known = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"missing@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(known).isEqualTo(unknown)
                .doesNotContain(email, "wrong-password", "Exception", "password_hash");
    }

    @Test
    void rejectsMissingInvalidAndExpiredAccessTokensAndDefaultsToProtected() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/future-resource")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        var now = java.time.Instant.now();
        var claims = org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().issuer("beehome")
                .subject(UUID.randomUUID().toString()).audience(java.util.List.of("beehome-api"))
                .issuedAt(now.minusSeconds(120)).expiresAt(now.minusSeconds(60)).build();
        String expired = encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(), claims)).getTokenValue();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").contentType("application/json").content("{\"refreshToken\":\"invalid\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredRefreshIsRejectedAndOnlyHashesAreStored() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        UUID userId = UUID.fromString(field(register(email), "id"));
        String raw = field(login(email, "Secure-password1!"), "refreshToken");
        String stored = jdbc.queryForObject("select token_hash from beehome.refresh_tokens where user_id = ?", String.class, userId);
        org.assertj.core.api.Assertions.assertThat(stored).hasSize(64).isNotEqualTo(raw);
        jdbc.update("update beehome.refresh_tokens set created_at = now() - interval '2 days', expires_at = now() - interval '1 day' where user_id = ?", userId);
        refresh(raw).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void passwordResetIsSingleUseChangesPasswordAndRevokesAllSessions() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        UUID id = UUID.fromString(field(register(email), "id"));
        String session = field(login(email, "Secure-password1!"), "refreshToken");
        String knownResponse = forgot(email);
        org.assertj.core.api.Assertions.assertThat(knownResponse).isEqualTo(forgot("unknown-" + email));
        var captured = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(delivery).send(org.mockito.ArgumentMatchers.eq(email), captured.capture());
        String raw = captured.getValue();
        org.assertj.core.api.Assertions.assertThat(knownResponse).doesNotContain(raw);
        String hash = jdbc.queryForObject("select token_hash from beehome.password_reset_tokens where user_id = ?", String.class, id);
        org.assertj.core.api.Assertions.assertThat(hash).hasSize(64).isNotEqualTo(raw);
        forgot(email);
        org.mockito.Mockito.verify(delivery, org.mockito.Mockito.times(1)).send(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString());
        reset(raw).andExpect(status().isOk());
        reset(raw).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        refresh(session).andExpect(status().isUnauthorized());
        login(email, "new-Secure-password1!");
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"Secure-password1!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredAndUnknownPasswordResetTokens() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        UUID id = UUID.fromString(field(register(email), "id"));
        forgot(email);
        var captured = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(delivery).send(org.mockito.ArgumentMatchers.eq(email), captured.capture());
        jdbc.update("update beehome.password_reset_tokens set created_at = now() - interval '2 days', expires_at = now() - interval '1 day' where user_id = ?", id);
        reset(captured.getValue()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EXPIRED_RESET_TOKEN"));
        reset("unknown").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        login(email, "Secure-password1!");
    }

    @Test
    void cannotLogoutAnotherUsersSessionOrChooseCurrentUser() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String other = UUID.randomUUID() + "@example.com";
        register(email);
        String otherId = field(register(other), "id");
        String access = field(login(email, "Secure-password1!"), "accessToken");
        String otherRefresh = field(login(other, "Secure-password1!"), "refreshToken");
        mvc.perform(get("/api/users/me").param("userId", otherId).header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access)
                .contentType("application/json").content("{\"refreshToken\":\"" + otherRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
        refresh(otherRefresh).andExpect(status().isOk());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "en,Invalid email or password,If an account exists for this email",
        "pt,Email ou senha inválidos,Se existir uma conta para este email",
        "es,Email o contraseña incorrectos,Si existe una cuenta para este email",
        "pt-BR,Email ou senha inválidos,Se existir uma conta para este email",
        "es-PY,Email o contraseña incorrectos,Si existe una cuenta para este email",
        "fr,Invalid email or password,If an account exists for this email"})
    void localizesErrorsAndSuccessWithStableCodes(String locale, String detail, String success) throws Exception {
        mvc.perform(post("/api/auth/login").header("Accept-Language", locale).contentType("application/json")
                .content("{\"email\":\"unknown@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value(detail));
        mvc.perform(post("/api/auth/forgot-password").header("Accept-Language", locale).contentType("application/json")
                .content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(success)));
    }

    @Test
    void storesUniqueProviderSubjectsIndependentlyOfProviderEmail() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        UUID id = UUID.fromString(field(register(email), "id"));
        String subject = UUID.randomUUID().toString();
        jdbc.update("insert into beehome.external_identities(id,user_id,provider,provider_subject,created_at) values (?,?,?,?,now())", UUID.randomUUID(), id, "GOOGLE", subject);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "insert into beehome.external_identities(id,user_id,provider,provider_subject,created_at) values (?,?,?,?,now())", UUID.randomUUID(), id, "GOOGLE", subject))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(com.beehome.auth.entity.ExternalProvider.class)
    void resolvesVerifiedProviderSubjectsWithoutCreatingOrMergingAccounts(com.beehome.auth.entity.ExternalProvider provider) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        UUID id = UUID.fromString(field(register(email), "id"));
        String subject = UUID.randomUUID().toString();
        jdbc.update("insert into beehome.external_identities(id,user_id,provider,provider_subject,created_at) values (?,?,?,?,now())",
                UUID.randomUUID(), id, provider.name(), subject);
        String issuer = provider == com.beehome.auth.entity.ExternalProvider.GOOGLE ? "https://accounts.google.com" : "https://appleid.apple.com";
        var identity = oidcIdentity(issuer, subject, "different@example.com");
        int before = jdbc.queryForObject("select count(*) from beehome.users", Integer.class);
        org.assertj.core.api.Assertions.assertThat(externalIdentities.resolve(provider, identity).id()).isEqualTo(id);
        org.assertj.core.api.Assertions.assertThat(externalIdentities.resolve(provider, identity).id()).isEqualTo(id);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from beehome.users", Integer.class)).isEqualTo(before);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> externalIdentities.resolve(provider, oidcIdentity(issuer, "unlinked", email)))
                .isInstanceOf(com.beehome.shared.exception.ApiException.class)
                .extracting(e -> ((com.beehome.shared.exception.ApiException) e).getCode()).isEqualTo("EXTERNAL_IDENTITY_NOT_LINKED");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> externalIdentities.resolve(provider, oidcIdentity("https://untrusted.example", subject, email)))
                .isInstanceOf(com.beehome.shared.exception.ApiException.class);
    }

    private org.springframework.security.oauth2.core.oidc.user.OidcUser oidcIdentity(String issuer, String subject, String email) {
        var token = new org.springframework.security.oauth2.core.oidc.OidcIdToken("test-provider-token", java.time.Instant.now(),
                java.time.Instant.now().plusSeconds(300), java.util.Map.of("iss", issuer, "sub", subject, "email", email));
        return new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(java.util.List.of(), token);
    }

    @Test
    void onlyOneConcurrentRefreshCanConsumeTheToken() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        register(email);
        String token = field(login(email, "Secure-password1!"), "refreshToken");
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Callable<Integer> attempt = () -> {
                start.await();
                return refresh(token).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            org.assertj.core.api.Assertions.assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 401);
        }
    }

    @Test
    @org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
    void concurrentRegistrationReturnsAStableConflictWithoutLoggingEmail(org.springframework.boot.test.system.CapturedOutput output) throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Callable<Integer> attempt = () -> {
                start.await();
                var response = mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"name\":\"Bruno\",\"email\":\"" + email + "\",\"password\":\"Secure-password1!\"}"))
                        .andReturn().getResponse();
                if (response.getStatus() == 409)
                    org.assertj.core.api.Assertions.assertThat(field(response.getContentAsString(), "code")).isEqualTo("EMAIL_ALREADY_REGISTERED");
                return response.getStatus();
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            org.assertj.core.api.Assertions.assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        org.assertj.core.api.Assertions.assertThat(output.getAll()).doesNotContain(email, "Secure-password1!");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("select count(*) from beehome.users where email = ?", Integer.class, email)).isEqualTo(1);
    }

    @Test
    void onlyOneConcurrentResetCanConsumeTheToken() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        register(email);
        forgot(email);
        var captured = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(delivery).send(org.mockito.ArgumentMatchers.eq(email), captured.capture());
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            java.util.concurrent.Callable<Integer> attempt = () -> {
                start.await();
                return reset(captured.getValue()).andReturn().getResponse().getStatus();
            };
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            start.countDown();
            org.assertj.core.api.Assertions.assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 400);
        }
    }

    @Test
    void deliveryFailureDoesNotDiscloseAccountExistence() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        register(email);
        org.mockito.Mockito.doThrow(new IllegalStateException("Sensitive provider details"))
                .when(delivery).send(org.mockito.ArgumentMatchers.eq(email), org.mockito.ArgumentMatchers.anyString());
        org.assertj.core.api.Assertions.assertThat(forgot(email)).isEqualTo(forgot("missing-" + email));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @org.junit.jupiter.params.provider.MethodSource("invalidPasswords")
    void rejectsInvalidPasswordsWithOneRequirementsMessage(String password) throws Exception {
        String value = password == null ? "null" : "\"" + password + "\"";
        for (boolean reset : new boolean[] {false, true}) {
            String field = reset ? "newPassword" : "password";
            String body = reset ? "{\"token\":\"unused-token\",\"newPassword\":" + value + "}"
                    : "{\"name\":\"Alex\",\"email\":\"" + UUID.randomUUID() + "@example.com\",\"password\":" + value + "}";
            mvc.perform(post(reset ? "/api/auth/reset-password" : "/api/auth/register")
                            .header("Accept-Language", "pt-BR").contentType("application/json").content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors.length()").value(1))
                    .andExpect(jsonPath("$.errors[0].field").value(field))
                    .andExpect(jsonPath("$.errors[0].message").value(
                            "A senha deve ter de 8 a 128 caracteres, incluindo pelo menos uma letra maiúscula, um número e um caractere especial."));
        }
    }

    static java.util.stream.Stream<String> invalidPasswords() {
        return java.util.stream.Stream.of("       ", "Abc12!x", "lowercase123!", "UppercaseOnly!",
                "NoSpecial1234", "SpacesOnly123 ", "short", "A1!" + "x".repeat(126));
    }

    static java.util.stream.Stream<String> validPasswords() {
        return java.util.stream.Stream.of("Abcdef1!", "ABCDEF1!", "A1!" + "x".repeat(125));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.MethodSource("validPasswords")
    void acceptsPasswordLengthBoundariesWithoutRequiringLowercase(String password) throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"name\":\"Alex\",\"email\":\"" + UUID.randomUUID()
                                + "@example.com\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void validatesPasswordAndDoesNotEchoRejectedValues() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"name\":\"\",\"email\":\"invalid\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("short"))));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"en,Authentication is required", "pt,Autenticação necessária",
            "es,Se requiere autenticación", "pt-BR,Autenticação necessária", "de,Authentication is required"})
    void localizesSecurityFilterFailures(String locale, String detail) throws Exception {
        mvc.perform(get("/api/users/me").header("Accept-Language", locale))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.detail").value(detail));
    }

    @Test
    void logsInAndRotatesAndRevokesSession() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("""
                        {"name":"Bruno","email":"%s","password":"Secure-password1!"}
                        """.formatted(email))).andExpect(status().isCreated());
        var login = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("""
                        {"email":"%s","password":"Secure-password1!"}
                        """.formatted(email))).andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String access = com.jayway.jsonpath.JsonPath.read(login, "$.accessToken");
        String refresh = com.jayway.jsonpath.JsonPath.read(login, "$.refreshToken");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));
        var rotated = mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isUnauthorized());
        String next = com.jayway.jsonpath.JsonPath.read(rotated, "$.refreshToken");
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + access)
                .contentType("application/json").content("{\"refreshToken\":\"" + next + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"" + next + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordRecoveryDoesNotRevealAccountExistence() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                .content("{\"email\":\"unknown@example.com\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void registersUserWithoutExposingPassword() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("""
                                {"name":"Bruno","email":"%s@example.com","password":"Secure-password1!"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Bruno"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
