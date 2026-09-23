package com.beehome;

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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "auth.allow-ephemeral-key=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FamilyMemberTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into beehome.users (id, name, email, created_at, updated_at) values (?, 'Test User', ?, now(), now())", id, id + "@example.com");
        return id;
    }

    UUID family(UUID user) throws Exception {
        return id(mvc.perform(post("/api/families").with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content("{\"name\":\"Family\",\"timezone\":\"UTC\"}")).andExpect(status().isCreated()));
    }

    UUID id(ResultActions result) throws Exception {
        return UUID.fromString(com.jayway.jsonpath.JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id"));
    }

    String path(UUID family) { return "/api/families/" + family + "/members"; }

    ResultActions create(UUID user, UUID family, String body) throws Exception {
        return mvc.perform(post(path(family)).with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content(body));
    }

    UUID member(UUID user, UUID family) throws Exception {
        return id(create(user, family, "{\"name\":\"Person\",\"memberType\":\"ADULT\"}").andExpect(status().isCreated()));
    }

    @Test
    void createsNormalizedPeopleWithOptionalFieldsAndPrivateResponse() throws Exception {
        UUID owner = user(), family = family(owner);
        var response = create(owner, family, "{\"name\":\"  Daniel  Amado  \",\"memberType\":\"CHILD\",\"birthDate\":\"2019-03-22\",\"color\":\"#a8d8ea\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Daniel  Amado"))
                .andExpect(jsonPath("$.color").value("#A8D8EA")).andExpect(jsonPath("$.birthDate").value("2019-03-22"))
                .andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.linkedUser").value(false))
                .andExpect(jsonPath("$.familyId").value(family.toString())).andExpect(jsonPath("$.avatarReference").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.linkedUserId").doesNotExist()).andExpect(jsonPath("$.createdAt").isString());
        UUID member = id(response);
        response.andExpect(header().string("Location", path(family) + "/" + member));
        member(owner, family);
        member(owner, family);
        assertThat(jdbc.queryForObject("select count(*) from beehome.family_members where family_id = ?", Integer.class, family)).isEqualTo(3);
    }

    @Test
    void rejectsInvalidCreationWithoutPersisting() throws Exception {
        UUID owner = user(), family = family(owner);
        for (String body : new String[]{"{}", "{\"name\":null,\"memberType\":\"ADULT\"}",
                "{\"name\":\" \",\"memberType\":\"ADULT\"}", "{\"name\":42,\"memberType\":\"ADULT\"}",
                "{\"name\":\"" + "a".repeat(121) + "\",\"memberType\":\"ADULT\"}",
                "{\"name\":\"P\",\"memberType\":\"OTHER\"}", "{\"name\":\"P\",\"memberType\":null}",
                "{\"name\":\"P\",\"memberType\":\"ADULT\",\"birthDate\":\"2999-01-01\"}",
                "{\"name\":\"P\",\"memberType\":\"ADULT\",\"birthDate\":\"2025-02-29\"}",
                "{\"name\":\"P\",\"memberType\":\"ADULT\",\"color\":\"red\"}"}) {
            create(owner, family, body).andExpect(status().isBadRequest());
        }
        for (String field : new String[]{"id", "familyId", "linkedUserId", "avatarReference", "active", "createdAt", "updatedAt", "unknown"}) {
            create(owner, family, "{\"name\":\"P\",\"memberType\":\"ADULT\",\"" + field + "\":null}").andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from beehome.family_members where family_id = ?", Integer.class, family)).isZero();
    }

    ResultActions request(UUID user, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(jwt().jwt(j -> j.subject(user.toString()))));
    }

    void membership(UUID family, UUID user, String role) {
        jdbc.update("insert into beehome.family_memberships (id, family_id, user_id, role, created_at) values (?, ?, ?, ?, now())",
                UUID.randomUUID(), family, user, role);
    }

    @Test
    void filtersOrdersAndReadsInactivePeople() throws Exception {
        UUID owner = user(), family = family(owner);
        UUID adult = member(owner, family);
        UUID child = id(create(owner, family, "{\"name\":\"Child\",\"memberType\":\"CHILD\"}").andExpect(status().isCreated()));
        String childPath = path(family) + "/" + child;
        request(owner, post(childPath + "/deactivate")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        request(owner, get(path(family))).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(adult.toString()));
        request(owner, get(path(family)).param("type", "CHILD")).andExpect(jsonPath("$.items.length()").value(0));
        request(owner, get(path(family)).param("includeInactive", "true").param("type", "CHILD"))
                .andExpect(jsonPath("$.items[0].id").value(child.toString()));
        request(owner, get(childPath)).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        var unchanged = request(owner, post(childPath + "/deactivate")).andReturn().getResponse().getContentAsString();
        request(owner, get(childPath)).andExpect(content().json(unchanged));
        request(owner, post(childPath + "/reactivate")).andExpect(jsonPath("$.active").value(true));
        jdbc.update("update beehome.family_members set created_at = '2026-01-01T00:00:00Z' where family_id = ?", family);
        var expected = java.util.stream.Stream.of(adult, child).map(UUID::toString).sorted().toList();
        request(owner, get(path(family))).andExpect(jsonPath("$.items[0].id").value(expected.get(0)))
                .andExpect(jsonPath("$.items[1].id").value(expected.get(1)));
        for (String query : new String[]{"?includeInactive=yes", "?includeInactive=TRUE", "?includeInactive=1", "?includeInactive=", "?type=adult", "?type=OTHER", "?type="}) {
            request(owner, get(path(family) + query)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void boundsPagesAndAppliesFamilyAndTypeFiltersBeforePagination() throws Exception {
        UUID owner = user(), family = family(owner), other = family(owner);
        member(owner, other);
        for (int i = 0; i < 21; i++) member(owner, family);
        UUID child = id(create(owner, family, "{\"name\":\"Child\",\"memberType\":\"CHILD\"}")
                .andExpect(status().isCreated()));
        request(owner, get(path(family)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.hasNext").value(true));
        request(owner, get(path(family)).param("page", "1"))
                .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.hasNext").value(false));
        request(owner, get(path(family)).param("page", "2"))
                .andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.hasNext").value(false));
        request(owner, get(path(family)).param("type", "CHILD").param("size", "1"))
                .andExpect(jsonPath("$.items[0].id").value(child.toString()))
                .andExpect(jsonPath("$.hasNext").value(false));
        request(owner, get(path(family)).param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(22));
        for (String query : new String[]{"?page=-1", "?size=0", "?size=101", "?page=2147483647&size=2", "?page=abc"}) {
            request(owner, get(path(family) + query)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void patchesFieldsIndependentlyAndPreservesOmittedValues() throws Exception {
        UUID owner = user(), family = family(owner), member = member(owner, family);
        String path = path(family) + "/" + member;
        for (String body : new String[]{"{\"name\":\" New name \"}", "{\"memberType\":\"CHILD\"}",
                "{\"birthDate\":\"2020-02-29\"}", "{\"color\":\"#aabbcc\"}"}) {
            request(owner, patch(path).contentType("application/json").content(body)).andExpect(status().isOk());
        }
        request(owner, get(path)).andExpect(jsonPath("$.name").value("New name"))
                .andExpect(jsonPath("$.memberType").value("CHILD")).andExpect(jsonPath("$.birthDate").value("2020-02-29"))
                .andExpect(jsonPath("$.color").value("#AABBCC"));
        request(owner, patch(path).contentType("application/json").content("{\"birthDate\":null,\"color\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.birthDate").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.color").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.name").value("New name"));
        String before = request(owner, get(path)).andReturn().getResponse().getContentAsString();
        request(owner, patch(path).contentType("application/json").content("{\"name\":\"New name\"}"))
                .andExpect(content().json(before));
        for (String body : new String[]{"{}", "{\"name\":null}", "{\"memberType\":null}", "{\"avatarReference\":null}",
                "{\"name\":42}", "{\"color\":false}", "{\"birthDate\":2999}", "{\"active\":false}",
                "{\"birthDate\":\"2999-01-01\"}", "{\"name\":\" \"}"}) {
            request(owner, patch(path).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        request(owner, get(path)).andExpect(content().json(before));
    }

    @Test
    void enforcesRolesFamilyBoundariesAndAnonymousAccess() throws Exception {
        UUID owner = user(), family = family(owner), member = member(owner, family);
        UUID reader = user(), admin = user(), stranger = user();
        membership(family, reader, "MEMBER");
        membership(family, admin, "ADMIN");
        String detail = path(family) + "/" + member;
        for (UUID allowed : new UUID[]{owner, admin}) {
            member(allowed, family);
            request(allowed, patch(detail).contentType("application/json").content("{\"name\":\"Edited\"}")).andExpect(status().isOk());
            request(allowed, post(detail + "/deactivate")).andExpect(status().isOk());
            request(allowed, post(detail + "/reactivate")).andExpect(status().isOk());
        }
        for (var action : java.util.List.of(get(path(family)), get(detail),
                post(path(family)).contentType("application/json").content("{\"name\":\"P\",\"memberType\":\"ADULT\"}"),
                patch(detail).contentType("application/json").content("{\"name\":\"Edited\"}"),
                post(detail + "/deactivate"), post(detail + "/reactivate"))) {
            mvc.perform(action).andExpect(status().isUnauthorized());
            request(stranger, action).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAMILY_NOT_FOUND"));
            request(reader, action).andExpect(action.buildRequest(new org.springframework.mock.web.MockServletContext()).getMethod().equals("GET") ? status().isOk() : status().isForbidden());
        }
        UUID otherFamily = family(reader);
        for (String suffix : new String[]{member.toString(), UUID.randomUUID().toString()}) {
            for (var action : java.util.List.of(get(path(otherFamily) + "/" + suffix),
                    patch(path(otherFamily) + "/" + suffix).contentType("application/json").content("{\"name\":\"Edited\"}"),
                    post(path(otherFamily) + "/" + suffix + "/deactivate"), post(path(otherFamily) + "/" + suffix + "/reactivate"))) {
                request(reader, action).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAMILY_MEMBER_NOT_FOUND"));
            }
        }
        request(stranger, get(path(family) + "?type=invalid")).andExpect(status().isNotFound());
    }

    @Test
    void linksAndUnlinksOwnAccountForEveryRoleWithIdempotencyAndNoAccessGrant() throws Exception {
        UUID owner = user(), family = family(owner);
        for (String role : new String[]{"OWNER", "ADMIN", "MEMBER"}) {
            UUID caller = role.equals("OWNER") ? owner : user();
            if (!caller.equals(owner)) membership(family, caller, role);
            UUID member = member(owner, family);
            String detail = path(family) + "/" + member;
            String linked = request(caller, put(detail + "/link-me")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.linkedUser").value(true)).andExpect(jsonPath("$.linkedUserId").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            request(caller, put(detail + "/link-me")).andExpect(status().isOk()).andExpect(content().json(linked));
            request(owner, post(detail + "/deactivate")).andExpect(status().isOk()).andExpect(jsonPath("$.linkedUser").value(true));
            request(caller, put(detail + "/link-me")).andExpect(status().isOk());
            request(caller, delete(detail + "/link")).andExpect(status().isNoContent()).andExpect(content().string(""));
            request(caller, delete(detail + "/link")).andExpect(role.equals("MEMBER") ? status().isForbidden() : status().isNoContent());
            request(caller, put(detail + "/link-me")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FAMILY_MEMBER_INACTIVE"));
            request(owner, post(detail + "/reactivate")).andExpect(status().isOk());
            request(caller, put(detail + "/link-me")).andExpect(status().isOk());
            request(owner, delete(detail + "/link")).andExpect(status().isNoContent());
        }
        assertThat(jdbc.queryForObject("select count(*) from beehome.family_memberships where family_id = ?", Integer.class, family)).isEqualTo(3);
    }

    @Test
    void rejectsConflictingLinksAndScopesLinkAuthorization() throws Exception {
        UUID owner = user(), family = family(owner), first = member(owner, family), second = member(owner, family);
        UUID reader = user(), stranger = user();
        membership(family, reader, "MEMBER");
        String firstPath = path(family) + "/" + first, secondPath = path(family) + "/" + second;
        request(owner, put(firstPath + "/link-me")).andExpect(status().isOk());
        request(reader, put(firstPath + "/link-me")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FAMILY_MEMBER_ALREADY_LINKED"));
        request(owner, put(secondPath + "/link-me")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USER_ALREADY_LINKED_TO_FAMILY_MEMBER"));
        request(reader, delete(firstPath + "/link")).andExpect(status().isForbidden());
        UUID otherFamily = family(owner), otherPerson = member(owner, otherFamily);
        request(owner, put(path(otherFamily) + "/" + otherPerson + "/link-me")).andExpect(status().isOk());
        for (var action : java.util.List.of(put(firstPath + "/link-me"), delete(firstPath + "/link"))) {
            mvc.perform(action).andExpect(status().isUnauthorized());
            request(stranger, action).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAMILY_NOT_FOUND"));
        }
        for (UUID missing : new UUID[]{otherPerson, UUID.randomUUID()}) {
            request(reader, put(path(family) + "/" + missing + "/link-me")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAMILY_MEMBER_NOT_FOUND"));
            request(reader, delete(path(family) + "/" + missing + "/link")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAMILY_MEMBER_NOT_FOUND"));
        }
    }

    @Test
    void serializesCompetingAccountsAndEnforcesOnePersonPerAccountConcurrently() throws Exception {
        UUID owner = user(), family = family(owner), reader = user();
        membership(family, reader, "MEMBER");
        UUID first = member(owner, family), second = member(owner, family);
        assertConcurrentLinks(java.util.List.of(owner, reader), java.util.List.of(first, first), family, "FAMILY_MEMBER_ALREADY_LINKED");
        request(owner, delete(path(family) + "/" + first + "/link")).andExpect(status().isNoContent());
        assertConcurrentLinks(java.util.List.of(owner, owner), java.util.List.of(first, second), family, "USER_ALREADY_LINKED_TO_FAMILY_MEMBER");
    }

    void assertConcurrentLinks(java.util.List<UUID> users, java.util.List<UUID> people, UUID family, String code) throws Exception {
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<org.springframework.mock.web.MockHttpServletResponse>>();
            for (int i = 0; i < 2; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    return request(users.get(index), put(path(family) + "/" + people.get(index) + "/link-me")).andReturn().getResponse();
                }));
            }
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var responses = java.util.List.of(futures.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS), futures.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(responses).extracting(org.springframework.mock.web.MockHttpServletResponse::getStatus).containsExactlyInAnyOrder(200, 409);
            for (var response : responses) if (response.getStatus() == 409) {
                assertThat((String) com.jayway.jsonpath.JsonPath.read(response.getContentAsString(), "$.code")).isEqualTo(code);
            }
        }
    }

    @Test
    void databaseEnforcesLinksMembershipAndStructuralConstraints() throws Exception {
        UUID owner = user(), family = family(owner), first = member(owner, family), second = member(owner, family);
        UUID outsider = user();
        for (String assignment : new String[]{"name = '   '", "name = E'\\t\\n'", "member_type = 'OTHER'", "color = '#abcdef'", "color = '#12345G'", "family_id = '" + UUID.randomUUID() + "'"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("update beehome.family_members set " + assignment + " where id = ?", first))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("update beehome.family_members set linked_user_id = ? where id = ?", outsider, first))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("update beehome.family_members set linked_user_id = ? where id = ?", owner, first);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("update beehome.family_members set linked_user_id = ? where id = ?", owner, second))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("delete from beehome.family_memberships where family_id = ? and user_id = ?", family, owner))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update("update beehome.family_members set linked_user_id = null where id = ?", first);
        jdbc.update("delete from beehome.family_memberships where family_id = ? and user_id = ?", family, owner);
        request(owner, get(path(family))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from beehome.family_members where family_id = ?", Integer.class, family)).isEqualTo(2);
    }

    @Test
    void localizesAllDomainErrorsWithoutDisclosingAccounts() throws Exception {
        UUID owner = user(), family = family(owner), first = member(owner, family), second = member(owner, family), inactive = member(owner, family), reader = user();
        membership(family, reader, "MEMBER");
        request(owner, put(path(family) + "/" + first + "/link-me")).andExpect(status().isOk());
        request(owner, post(path(family) + "/" + inactive + "/deactivate")).andExpect(status().isOk());
        for (String language : new String[]{"en", "pt", "es", "pt-BR", "es-PY", "fr", ""}) {
            String base = language.startsWith("pt") ? "pt" : language.startsWith("es") ? "es" : "en";
            var bundle = java.util.ResourceBundle.getBundle("messages", java.util.Locale.forLanguageTag(base));
            var actions = java.util.List.of(get(path(family) + "/" + UUID.randomUUID()),
                    put(path(family) + "/" + first + "/link-me"), put(path(family) + "/" + second + "/link-me"),
                    put(path(family) + "/" + inactive + "/link-me"));
            String[] keys = {"not-found", "already-linked", "user-already-linked", "inactive"};
            String[] codes = {"FAMILY_MEMBER_NOT_FOUND", "FAMILY_MEMBER_ALREADY_LINKED", "USER_ALREADY_LINKED_TO_FAMILY_MEMBER", "FAMILY_MEMBER_INACTIVE"};
            for (int i = 0; i < actions.size(); i++) {
                var action = actions.get(i);
                if (!language.isEmpty()) action.header("Accept-Language", language);
                request(i == 1 ? reader : owner, action).andExpect(status().is(i == 0 ? 404 : 409))
                        .andExpect(jsonPath("$.code").value(codes[i]))
                        .andExpect(jsonPath("$.detail").value(bundle.getString("error.family-member." + keys[i])))
                        .andExpect(jsonPath("$.linkedUserId").doesNotExist());
            }
            var invalid = post(path(family)).contentType("application/json").content("{\"name\":\" \",\"memberType\":\"ADULT\"}");
            if (!language.isEmpty()) invalid.header("Accept-Language", language);
            request(owner, invalid).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.errors[0].message").value(bundle.getString("validation.family-member.name")));
        }
    }

    @Test
    void advertisesEveryRouteAndPresenceAwareSchemaInOpenApi() throws Exception {
        var result = mvc.perform(get("/v3/api-docs").with(jwt())).andExpect(status().isOk());
        String collection = "$.paths['/api/families/{familyId}/members']";
        String detail = "$.paths['/api/families/{familyId}/members/{memberId}";
        result.andExpect(jsonPath(collection + ".post.responses['201']").exists())
                .andExpect(jsonPath(collection + ".get.responses['200']").exists())
                .andExpect(jsonPath(collection + ".post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath(detail + "'].get.responses['200']").exists())
                .andExpect(jsonPath(detail + "'].patch.responses['200']").exists());
        for (String action : new String[]{"deactivate", "reactivate"}) result.andExpect(jsonPath(detail + "/" + action + "'].post.responses['200']").exists());
        result.andExpect(jsonPath(detail + "/link-me'].put.responses['409']").exists())
                .andExpect(jsonPath(detail + "/link'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.components.schemas.PatchFamilyMemberRequest.properties.fields").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PatchFamilyMemberRequest.properties.birthDate").exists());
    }
}
