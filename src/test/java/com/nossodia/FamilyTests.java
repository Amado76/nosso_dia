package com.nossodia;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "auth.allow-ephemeral-key=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FamilyTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into nosso_dia.users (id, name, email, created_at, updated_at) values (?, 'Test User', ?, now(), now())",
                id, id + "@example.com");
        return id;
    }

    ResultActions create(UUID user, String body) throws Exception {
        return mvc.perform(post("/api/families").with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content(body));
    }

    UUID family(UUID user) throws Exception {
        String body = create(user, "{\"name\":\"Amado Family\",\"timezone\":\"UTC\"}").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));
    }

    ResultActions read(UUID user, UUID family) throws Exception {
        return mvc.perform(get("/api/families/" + family).with(jwt().jwt(j -> j.subject(user.toString()))));
    }

    ResultActions rename(UUID user, UUID family, String body) throws Exception {
        return mvc.perform(patch("/api/families/" + family).with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content(body));
    }

    @Test
    void createsFamilyAndOwnerAndReturnsNormalizedPrivateRepresentation() throws Exception {
        UUID owner = user();
        String body = create(owner, "{\"name\":\"  Família Amado  \",\"timezone\":\"UTC\"}")
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Família Amado"))
                .andExpect(jsonPath("$.myRole").value("OWNER"))
                .andExpect(jsonPath("$.createdAt").isString()).andExpect(jsonPath("$.updatedAt").isString())
                .andExpect(jsonPath("$.userId").doesNotExist()).andExpect(jsonPath("$.memberships").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(com.jayway.jsonpath.JsonPath.read(body, "$.id"));
        assertThat(jdbc.queryForObject("select role from nosso_dia.family_memberships where family_id = ? and user_id = ?",
                String.class, id, owner)).isEqualTo("OWNER");
        read(owner, id).andExpect(status().isOk()).andExpect(content().json(body));
        family(owner); // Multiple families and duplicate names are allowed.
    }

    @Test
    void scopesReadsWritesAndPaginationToMembership() throws Exception {
        UUID owner = user();
        UUID stranger = user();
        UUID hidden = family(stranger);
        UUID first = family(owner);
        UUID second = family(owner);
        for (UUID inaccessible : new UUID[]{hidden, UUID.randomUUID()}) {
            read(owner, inaccessible).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("FAMILY_NOT_FOUND"));
            rename(owner, inaccessible, "{\"name\":\"Intrusion\"}").andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("FAMILY_NOT_FOUND"));
        }
        for (int page = 0; page < 3; page++) {
            var result = mvc.perform(get("/api/families").param("page", "" + page).param("size", "1")
                    .with(jwt().jwt(j -> j.subject(owner.toString())))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.hasNext").value(page == 0))
                    .andExpect(jsonPath("$.page").value(page)).andExpect(jsonPath("$.size").value(1));
            if (page < 2) result.andExpect(jsonPath("$.items[0].id").value((page == 0 ? first : second).toString()));
            else result.andExpect(jsonPath("$.items").isEmpty());
        }
        UUID empty = user();
        mvc.perform(get("/api/families").with(jwt().jwt(j -> j.subject(empty.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void authorizesRenameUsingRoleInTheRequestedFamily() throws Exception {
        UUID owner = user();
        UUID admin = user();
        UUID member = user();
        UUID id = family(owner);
        family(member); // Global ownership must not grant permission here.
        for (var entry : java.util.Map.of(admin, "ADMIN", member, "MEMBER").entrySet()) {
            jdbc.update("insert into nosso_dia.family_memberships (id, family_id, user_id, role, created_at) values (?, ?, ?, ?, now())",
                    UUID.randomUUID(), id, entry.getKey(), entry.getValue());
            read(entry.getKey(), id).andExpect(status().isOk()).andExpect(jsonPath("$.myRole").value(entry.getValue()));
        }
        rename(member, id, "{\"name\":\"Forbidden\"}").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        for (UUID permitted : new UUID[]{owner, admin}) {
            rename(permitted, id, "{\"name\":\"  New Family Name  \"}").andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("New Family Name"));
        }
        jdbc.update("update nosso_dia.family_memberships set role = 'MEMBER' where family_id = ? and user_id = ?", id, admin);
        rename(admin, id, "{\"name\":\"Stale permission\"}").andExpect(status().isForbidden());
        read(owner, id).andExpect(jsonPath("$.name").value("New Family Name"));
    }

    @Test
    void rejectsInvalidNamesAndIdentityFieldsWithoutMutating() throws Exception {
        UUID owner = user();
        UUID id = family(owner);
        for (String body : new String[]{"{}", "{\"name\":null}", "{\"name\":\" \"}",
                "{\"name\":\"" + "a".repeat(121) + "\"}", "{\"name\":42}",
                "{\"name\":\"Valid\",\"role\":\"OWNER\"}", "{\"name\":\"Valid\",\"userId\":\"" + owner + "\"}"}) {
            create(owner, body).andExpect(status().isBadRequest());
            rename(owner, id, body).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from nosso_dia.family_memberships where user_id = ?", Integer.class, owner)).isEqualTo(1);
        read(owner, id).andExpect(jsonPath("$.name").value("Amado Family"));
        for (String query : new String[]{"?page=-1", "?size=0", "?size=101", "?page=no", "?page=2147483647&size=100"}) {
            mvc.perform(get("/api/families" + query).with(jwt().jwt(j -> j.subject(owner.toString()))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void requiresAuthenticationForEveryOperation() throws Exception {
        String path = "/api/families/" + UUID.randomUUID();
        for (var request : java.util.List.of(get("/api/families"), get(path),
                post("/api/families").contentType("application/json").content("{\"name\":\"Family\"}"),
                patch(path).contentType("application/json").content("{\"name\":\"Family\"}"))) {
            mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mvc.perform(request.header("Authorization", "Bearer invalid")).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void rollsBackFamilyWhenInitialMembershipCannotBePersisted() throws Exception {
        UUID owner = user();
        int before = jdbc.queryForObject("select count(*) from nosso_dia.families", Integer.class);
        // Inject a database failure after the family insert, without replacing transaction infrastructure.
        jdbc.execute("""
                create function nosso_dia.reject_test_membership() returns trigger language plpgsql as $$
                begin raise exception 'Injected membership failure'; end; $$
                """);
        try {
            jdbc.execute("create trigger reject_test_membership before insert on nosso_dia.family_memberships "
                    + "for each row when (new.user_id = '" + owner + "'::uuid) execute function nosso_dia.reject_test_membership()");
            create(owner, "{\"name\":\"Rollback Family\",\"timezone\":\"UTC\"}").andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
            assertThat(jdbc.queryForObject("select count(*) from nosso_dia.families", Integer.class)).isEqualTo(before);
            assertThat(jdbc.queryForObject("select count(*) from nosso_dia.family_memberships where user_id = ?", Integer.class, owner)).isZero();
        } finally {
            jdbc.execute("drop trigger if exists reject_test_membership on nosso_dia.family_memberships");
            jdbc.execute("drop function nosso_dia.reject_test_membership()");
        }
    }

    void insertMembership(UUID family, UUID user, String role) {
        jdbc.update("insert into nosso_dia.family_memberships (id, family_id, user_id, role, created_at) values (?, ?, ?, ?, now())",
                UUID.randomUUID(), family, user, role);
    }

    @Test
    void databaseEnforcesMembershipUniquenessOwnershipAndReferences() throws Exception {
        UUID owner = user();
        UUID other = user();
        UUID id = family(owner);
        for (Runnable invalid : java.util.List.<Runnable>of(
                () -> insertMembership(id, owner, "MEMBER"),
                () -> insertMembership(id, other, "OWNER"),
                () -> insertMembership(id, other, "SUPERUSER"),
                () -> insertMembership(UUID.randomUUID(), other, "MEMBER"),
                () -> insertMembership(id, UUID.randomUUID(), "MEMBER"))) {
            assertThatThrownBy(invalid::run).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        assertThat(jdbc.queryForObject("select count(*) from nosso_dia.family_memberships where family_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void concurrentMembershipInsertsCannotCreateDuplicates() throws Exception {
        UUID id = family(user());
        UUID member = user();
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> insert = () -> {
            ready.countDown();
            assertThat(start.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            try {
                insertMembership(id, member, "MEMBER");
                return true;
            } catch (org.springframework.dao.DataIntegrityViolationException expected) {
                return false;
            }
        };
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(insert);
            var second = executor.submit(insert);
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(10, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void breaksPaginationTiesByIdAndLocalizesErrors() throws Exception {
        UUID owner = user();
        UUID first = family(owner);
        UUID second = family(owner);
        jdbc.update("update nosso_dia.families set created_at = '2026-01-01T00:00:00Z' where id in (?, ?)", first, second);
        var expected = java.util.stream.Stream.of(first, second).map(UUID::toString).sorted().toList();
        mvc.perform(get("/api/families").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(jsonPath("$.items[0].id").value(expected.get(0)))
                .andExpect(jsonPath("$.items[1].id").value(expected.get(1)));
        mvc.perform(get("/api/families/" + UUID.randomUUID()).header("Accept-Language", "pt-BR")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Família não encontrada"));
        create(owner, "{\"name\":\"\"}").andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void advertisesFamilySecurityAndSuccessResponsesInOpenApi() throws Exception {
        mvc.perform(get("/v3/api-docs").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/families'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/families'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/families/{familyId}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/families/{familyId}'].patch.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/families'].post.security[0].bearerAuth").isArray());
    }
}
