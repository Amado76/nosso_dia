package com.beehome;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "auth.allow-ephemeral-key=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DrawingTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    private UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into beehome.users (id, name, email, created_at, updated_at) values (?, 'Artist', ?, now(), now())", id, id + "@example.com");
        return id;
    }

    private UUID family(UUID owner) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into beehome.families (id, name, timezone, created_at, updated_at) values (?, 'Family', 'UTC', now(), now())", id);
        jdbc.update("insert into beehome.family_memberships (id, family_id, user_id, role, created_at) values (?, ?, ?, 'OWNER', now())", UUID.randomUUID(), id, owner);
        return id;
    }

    private UUID member(UUID family) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into beehome.family_members (id, family_id, name, member_type, active, created_at, updated_at) values (?, ?, 'Artist', 'ADULT', true, now(), now())", id, family);
        return id;
    }

    private String path(UUID family, UUID member) {
        return "/api/families/" + family + "/members/" + member + "/drawings/profile-scratchpad";
    }

    @Test
    void returnsVirtualEmptyDocumentThenPersistsAndClearsWithRevisions() throws Exception {
        UUID owner = user(), family = family(owner), member = member(family);
        String path = path(family, member);
        mvc.perform(get(path).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(0))
                .andExpect(jsonPath("$.formatVersion").value(1)).andExpect(jsonPath("$.strokes").isEmpty());
        assertThat(jdbc.queryForObject("select count(*) from beehome.drawings where family_id = ? and member_id = ?", Integer.class, family, member)).isZero();
        String document = """
                {"formatVersion":1,"revision":0,"strokes":[{"id":"8e9bb12d-8296-45a4-9c91-d17ddae12be3","color":"#202124","width":3.0,"points":[{"x":0.124,"y":0.351},{"x":0.126,"y":0.354,"pressure":0.73}]}]}
                """;
        mvc.perform(put(path).with(jwt().jwt(j -> j.subject(owner.toString()))).contentType("application/json").content(document))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.strokes[0].points[1].pressure").value(0.73));
        mvc.perform(get(path).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.strokes[0].points[0].pressure").doesNotExist());
        assertThat(jdbc.queryForObject("select jsonb_exists(document #> '{strokes,0,points,0}', 'pressure') from beehome.drawings where family_id = ? and member_id = ?",
                Boolean.class, family, member)).isFalse();
        mvc.perform(put(path).with(jwt().jwt(j -> j.subject(owner.toString()))).contentType("application/json").content(document))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DRAWING_VERSION_CONFLICT"));
        mvc.perform(put(path).with(jwt().jwt(j -> j.subject(owner.toString()))).contentType("application/json")
                .content("{\"formatVersion\":1,\"revision\":1,\"strokes\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2))
                .andExpect(jsonPath("$.strokes").isEmpty());
    }

    @Test
    void hidesInaccessibleMembersBeforeValidatingTheirDrawing() throws Exception {
        UUID owner = user(), family = family(owner), member = member(family), stranger = user();
        mvc.perform(put(path(family, member)).with(jwt().jwt(j -> j.subject(stranger.toString())))
                        .contentType("application/json").content("{\"formatVersion\":99}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAMILY_NOT_FOUND"));
    }

    @Test
    void validatesDocumentStructureAndPreservesPreviousRevision() throws Exception {
        UUID owner = user(), family = family(owner), member = member(family);
        String path = path(family, member);
        String stroke = """
                {"id":"8e9bb12d-8296-45a4-9c91-d17ddae12be3","color":"#202124","width":3,"points":[{"x":0,"y":1}]}
                """.strip();
        for (String body : new String[]{
                "{\"formatVersion\":2,\"revision\":0,\"strokes\":[]}",
                "{\"formatVersion\":1,\"revision\":0,\"strokes\":[],\"unknown\":true}",
                "{\"formatVersion\":1,\"revision\":0,\"strokes\":[" + stroke.replace("#202124", "red") + "]}",
                "{\"formatVersion\":1,\"revision\":0,\"strokes\":[" + stroke.replace("\"width\":3", "\"width\":0") + "]}",
                "{\"formatVersion\":1,\"revision\":0,\"strokes\":[" + stroke.replace("\"x\":0", "\"x\":1.1") + "]}",
                "{\"formatVersion\":1,\"revision\":0,\"strokes\":[" + stroke.replace("\"y\":1", "\"y\":1,\"pressure\":null") + "]}",
                "{\"formatVersion\":1,\"revision\":0,\"strokes\":[" + stroke + "," + stroke + "]}"
        }) {
            mvc.perform(put(path).with(jwt().jwt(j -> j.subject(owner.toString()))).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").isString());
        }
        mvc.perform(get(path).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(0));
    }

    @Test
    void isolatesMemberDocumentsAndUsesFamilyRoles() throws Exception {
        UUID owner = user(), family = family(owner), first = member(family), second = member(family), reader = user(), stranger = user();
        jdbc.update("insert into beehome.family_memberships (id, family_id, user_id, role, created_at) values (?, ?, ?, 'MEMBER', now())", UUID.randomUUID(), family, reader);
        String body = "{\"formatVersion\":1,\"revision\":0,\"strokes\":[]}";
        String path = path(family, first);
        mvc.perform(put(path).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(put(path).with(jwt().jwt(j -> j.subject(reader.toString()))).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put(path).with(jwt().jwt(j -> j.subject(owner.toString()))).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        mvc.perform(get(path).with(jwt().jwt(j -> j.subject(reader.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        mvc.perform(get(path).with(jwt().jwt(j -> j.subject(stranger.toString()))))
                .andExpect(status().isNotFound());
        mvc.perform(get(path(family, second)).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(0));
        UUID otherFamily = family(owner);
        mvc.perform(get(path(otherFamily, first)).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAMILY_MEMBER_NOT_FOUND"));
    }

    @Test
    void rejectsRawRequestOverDocumentLimitWithoutSaving() throws Exception {
        UUID owner = user(), family = family(owner), member = member(family);
        String body = "{\"formatVersion\":1,\"revision\":0,\"strokes\":[]}" + " ".repeat(5 * 1024 * 1024);
        mvc.perform(put(path(family, member)).with(jwt().jwt(j -> j.subject(owner.toString())))
                        .contentType("application/json").content(body))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("DRAWING_TOO_LARGE"));
        assertThat(jdbc.queryForObject("select count(*) from beehome.drawings where family_id = ? and member_id = ?", Integer.class, family, member)).isZero();
    }

    @Test
    void replacesMultipleStrokesAndRemovesOneWithoutAffectingAnotherMember() throws Exception {
        UUID owner = user(), family = family(owner), first = member(family), second = member(family);
        String firstStroke = """
                {"id":"8e9bb12d-8296-45a4-9c91-d17ddae12be3","color":"#202124","width":3,"points":[{"x":0.1,"y":0.2}]}
                """.strip();
        String secondStroke = """
                {"id":"4ec3f4e2-ae79-40dd-813b-58ff57bb668d","color":"#ABCDEF","width":2,"points":[{"x":1,"y":0,"pressure":0}]}
                """.strip();
        String path = path(family, first);
        mvc.perform(put(path).with(jwt().jwt(j -> j.subject(owner.toString()))).contentType("application/json")
                .content("{\"formatVersion\":1,\"revision\":0,\"strokes\":[" + firstStroke + "," + secondStroke + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.strokes.length()").value(2));
        mvc.perform(put(path).with(jwt().jwt(j -> j.subject(owner.toString()))).contentType("application/json")
                .content("{\"formatVersion\":1,\"revision\":1,\"strokes\":[" + secondStroke + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(2));
        mvc.perform(get(path).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(jsonPath("$.strokes.length()").value(1))
                .andExpect(jsonPath("$.strokes[0].id").value("4ec3f4e2-ae79-40dd-813b-58ff57bb668d"))
                .andExpect(jsonPath("$.strokes[0].points[0].pressure").value(0));
        mvc.perform(get(path(family, second)).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(jsonPath("$.revision").value(0));
    }

    @Test
    void acceptsOnlyOneOfTwoConcurrentUpdatesAtTheSameRevision() throws Exception {
        UUID owner = user(), family = family(owner), member = member(family);
        String path = path(family, member);
        String body = "{\"formatVersion\":1,\"revision\":0,\"strokes\":[]}";
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var writes = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int index = 0; index < 2; index++) {
                writes.add(pool.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return mvc.perform(put(path).with(jwt().jwt(j -> j.subject(owner.toString())))
                            .contentType("application/json").content(body)).andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(writes.get(0).get(15, TimeUnit.SECONDS), writes.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from beehome.drawings where family_id = ? and member_id = ?", Integer.class, family, member)).isEqualTo(1);
    }
}
