package com.beehome;

import java.util.UUID;
import java.time.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "auth.allow-ephemeral-key=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StudyTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @org.springframework.test.context.bean.override.mockito.MockitoBean Clock clock;
    @org.junit.jupiter.api.BeforeEach void time() { at("2026-09-21T15:00:00Z"); }
    void at(String instant) {
        org.mockito.Mockito.when(clock.instant()).thenReturn(Instant.parse(instant));
        org.mockito.Mockito.doAnswer(i -> Clock.fixed(clock.instant(), i.getArgument(0)))
                .when(clock).withZone(org.mockito.ArgumentMatchers.any());
    }
    UUID user() {
        UUID user = UUID.randomUUID();
        jdbc.update("insert into beehome.users(id,name,email,created_at,updated_at) values (?, 'Study', ?, now(), now())", user, user + "@example.com");
        return user;
    }
    String call(UUID user, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(jwt().jwt(j -> j.subject(user.toString())))).andReturn().getResponse().getContentAsString();
    }
    String family(UUID user) throws Exception {
        String json = call(user, post("/api/families").contentType("application/json")
                .content("{\"name\":\"Study\",\"timezone\":\"America/Asuncion\"}"));
        return "/api/families/" + com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }
    String member(UUID user, String family) throws Exception {
        String json = call(user, post(family + "/members").contentType("application/json")
                .content("{\"name\":\"Child\",\"memberType\":\"CHILD\"}"));
        return family + "/members/" + com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }

    @Test void createsAndListsNormalizedSubjectsForFamilyMembers() throws Exception {
        UUID user = user();
        String base = family(user) + "/study-subjects";
        mvc.perform(post(base).with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content("{\"name\":\"  Matemática  \",\"sortOrder\":2}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Matemática"));
        mvc.perform(post(base).with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content("{\"name\":\"matemática\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDY_SUBJECT_DUPLICATE"));
        mvc.perform(get(base).with(jwt().jwt(j -> j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Matemática"));
    }

    @Test void timerCountsOnlyRunningIntervalsAndSummaryExcludesVoidedSessions() throws Exception {
        UUID user = user(); String family = family(user), member = member(user, family);
        String base = member + "/study-sessions";
        String start = mvc.perform(post(base + "/start").with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content("{}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.date").value("2026-09-21"))
                .andExpect(jsonPath("$.status").value("RUNNING")).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(start, "$.id");
        mvc.perform(post(base + "/start").with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDY_CONFLICT"));
        at("2026-09-21T15:10:00Z");
        mvc.perform(post(base + "/" + id + "/pause").with(jwt().jwt(j -> j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accumulatedDurationSeconds").value(600));
        at("2026-09-21T16:00:00Z");
        mvc.perform(post(base + "/" + id + "/resume").with(jwt().jwt(j -> j.subject(user.toString()))))
                .andExpect(status().isOk());
        at("2026-09-21T16:05:00Z");
        mvc.perform(post(base + "/" + id + "/finish").with(jwt().jwt(j -> j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accumulatedDurationSeconds").value(900));
        String query = "?from=2026-09-21&to=2026-09-21";
        mvc.perform(get(member + "/study-summary" + query).with(jwt().jwt(j -> j.subject(user.toString()))))
                .andExpect(jsonPath("$.totalDurationSeconds").value(900))
                .andExpect(jsonPath("$.subjects.length()").value(0));
        mvc.perform(post(base + "/" + id + "/void").with(jwt().jwt(j -> j.subject(user.toString()))))
                .andExpect(status().isOk());
        mvc.perform(get(member + "/study-summary" + query).with(jwt().jwt(j -> j.subject(user.toString()))))
                .andExpect(jsonPath("$.totalDurationSeconds").value(0));
        mvc.perform(get(base + query).with(jwt().jwt(j -> j.subject(user.toString()))))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test void manualSessionCanBeCorrectedButTimerDurationCannot() throws Exception {
        UUID owner = user(), reader = user(); String family = family(owner), member = member(owner, family);
        jdbc.update("insert into beehome.family_memberships(id,family_id,user_id,role,created_at) values (?, ?, ?, 'MEMBER', now())",
                UUID.randomUUID(), UUID.fromString(family.substring(family.lastIndexOf('/') + 1)), reader);
        String base = member + "/study-sessions";
        String json = mvc.perform(post(base).with(jwt().jwt(j -> j.subject(reader.toString())))
                .contentType("application/json").content("{\"date\":\"2026-09-20\",\"durationSeconds\":120}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.createdByUserId").value(reader.toString()))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(json, "$.id");
        mvc.perform(patch(base + "/" + id).with(jwt().jwt(j -> j.subject(reader.toString())))
                .contentType("application/json").content("{\"durationSeconds\":180}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(base + "/" + id).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"durationSeconds\":180}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accumulatedDurationSeconds").value(180));
        String timer = mvc.perform(post(base + "/start").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String timerId = com.jayway.jsonpath.JsonPath.read(timer, "$.id");
        mvc.perform(post(base + "/" + timerId + "/finish").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk());
        mvc.perform(patch(base + "/" + timerId).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"durationSeconds\":180}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STUDY_INVALID_STATE"));
    }

    @Test void subjectSnapshotAndMemberIsolationSurviveSubjectChanges() throws Exception {
        UUID owner = user(), outsider = user(); String family = family(owner), member = member(owner, family);
        String otherMember = member(owner, family);
        String subjects = family + "/study-subjects", base = member + "/study-sessions";
        String created = mvc.perform(post(subjects).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"Biology\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String subject = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        String session = mvc.perform(post(base).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2026-09-21\",\"durationSeconds\":90,\"subjectId\":\"" + subject + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.subjectNameSnapshot").value("Biology"))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(session, "$.id");
        mvc.perform(get(member + "/study-summary?from=2026-09-21&to=2026-09-21")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(jsonPath("$.totalDurationSeconds").value(90))
                .andExpect(jsonPath("$.subjects[0].subjectId").value(subject))
                .andExpect(jsonPath("$.subjects[0].sessionCount").value(1));
        mvc.perform(patch(subjects + "/" + subject).with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"Life Science\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(subjects + "/" + subject + "/deactivate").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk());
        mvc.perform(get(base + "/" + id).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subjectNameSnapshot").value("Biology"));
        mvc.perform(get(otherMember + "/study-sessions/" + id).with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STUDY_SESSION_NOT_FOUND"));
        mvc.perform(get(base + "/" + id).with(jwt().jwt(j -> j.subject(outsider.toString()))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAMILY_NOT_FOUND"));
        mvc.perform(get(base + "/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(post(base + "/start").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"subjectId\":\"" + subject + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test void executionLinkMustBelongToTheSameMemberAndTimerKeepsStartDateAcrossMidnight() throws Exception {
        UUID owner = user(); String family = family(owner), member = member(owner, family), other = member(owner, family);
        UUID familyId = UUID.fromString(family.substring(family.lastIndexOf('/') + 1));
        UUID otherId = UUID.fromString(other.substring(other.lastIndexOf('/') + 1));
        UUID execution = UUID.randomUUID(), item = UUID.randomUUID();
        jdbc.update("insert into beehome.daily_executions(id,family_id,family_member_id,execution_date,status,reopened,created_at,updated_at) values (?, ?, ?, '2026-09-21', 'OPEN', false, now(), now())",
                execution, familyId, otherId);
        jdbc.update("insert into beehome.daily_execution_items(id,execution_id,source_type,title,sort_order,status,created_at,updated_at) values (?, ?, 'DAILY_PLAN', 'Reading', 0, 'PENDING', now(), now())",
                item, execution);
        String base = member + "/study-sessions";
        at("2026-09-22T02:59:59Z");
        mvc.perform(post(base + "/start").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"dailyExecutionItemId\":\"" + item + "\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("STUDY_EXECUTION_ITEM_NOT_FOUND"));
        String otherBase = other + "/study-sessions";
        String session = mvc.perform(post(otherBase + "/start").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"dailyExecutionItemId\":\"" + item + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.date").value("2026-09-21"))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(session, "$.id");
        at("2026-09-22T03:00:01Z");
        mvc.perform(post(otherBase + "/" + id + "/finish").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.date").value("2026-09-21"))
                .andExpect(jsonPath("$.accumulatedDurationSeconds").value(2));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select status from beehome.daily_execution_items where id = ?", String.class, item)).isEqualTo("PENDING");
    }

    @Test void concurrentStartsLeaveOnlyOneRunningSession() throws Exception {
        UUID owner = user(); String family = family(owner), member = member(owner, family);
        String route = member + "/study-sessions/start";
        CountDownLatch ready = new CountDownLatch(2), go = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Callable<Integer> start = () -> {
                ready.countDown(); go.await();
                return mvc.perform(post(route).with(jwt().jwt(j -> j.subject(owner.toString())))
                        .contentType("application/json").content("{}"))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> first = workers.submit(start), second = workers.submit(start);
            org.assertj.core.api.Assertions.assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            org.assertj.core.api.Assertions.assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from beehome.study_sessions where family_member_id = ? and status = 'RUNNING'",
                Integer.class, UUID.fromString(member.substring(member.lastIndexOf('/') + 1)))).isEqualTo(1);
        String current = call(owner, get(member + "/study-sessions/current"));
        String firstId = com.jayway.jsonpath.JsonPath.read(current, "$.id");
        mvc.perform(post(member + "/study-sessions/" + firstId + "/pause")
                .with(jwt().jwt(j -> j.subject(owner.toString())))).andExpect(status().isOk());
        String secondSession = call(owner, post(route).contentType("application/json").content("{}"));
        String secondId = com.jayway.jsonpath.JsonPath.read(secondSession, "$.id");
        mvc.perform(post(member + "/study-sessions/" + secondId + "/pause")
                .with(jwt().jwt(j -> j.subject(owner.toString())))).andExpect(status().isOk());
        CountDownLatch resumeReady = new CountDownLatch(2), resumeGo = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            java.util.function.Function<String, Callable<Integer>> resume = id -> () -> {
                resumeReady.countDown(); resumeGo.await();
                return mvc.perform(post(member + "/study-sessions/" + id + "/resume")
                        .with(jwt().jwt(j -> j.subject(owner.toString()))))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> first = workers.submit(resume.apply(firstId)), second = workers.submit(resume.apply(secondId));
            org.assertj.core.api.Assertions.assertThat(resumeReady.await(5, TimeUnit.SECONDS)).isTrue();
            resumeGo.countDown();
            org.assertj.core.api.Assertions.assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
    }

    @Autowired com.beehome.study.service.StudyService study;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test void concurrentRenamePreservesSubjectDeactivation() throws Exception {
        UUID owner = user(); String family = family(owner);
        UUID familyId = UUID.fromString(family.substring(family.lastIndexOf('/') + 1));
        var created = study.createSubject(owner, familyId,
                com.beehome.study.dto.StudySubjectRequest.fromJson(java.util.Map.of("name", "Biology")));
        overlapWrites(
                () -> study.activeSubject(owner, familyId, created.id(), false),
                () -> study.patchSubject(owner, familyId, created.id(),
                        com.beehome.study.dto.StudySubjectRequest.fromJson(java.util.Map.of("name", "Science"))));
        var result = study.getSubject(owner, familyId, created.id());
        org.assertj.core.api.Assertions.assertThat(result.name()).isEqualTo("Science");
        org.assertj.core.api.Assertions.assertThat(result.active()).isFalse();
    }

    @Test void concurrentSubjectCreatesRespectFamilyLimit() throws Exception {
        UUID owner = user(); String family = family(owner);
        UUID familyId = UUID.fromString(family.substring(family.lastIndexOf('/') + 1));
        jdbc.update("""
                insert into beehome.study_subjects(id,family_id,name,normalized_name,active,sort_order,created_at,updated_at)
                select gen_random_uuid(), ?, 'Subject ' || n, 'subject ' || n, true, 0, now(), now()
                from generate_series(1,999) n
                """, familyId);
        Object result = overlapWrites(
                () -> study.createSubject(owner, familyId,
                        com.beehome.study.dto.StudySubjectRequest.fromJson(java.util.Map.of("name", "Last"))),
                () -> study.createSubject(owner, familyId,
                        com.beehome.study.dto.StudySubjectRequest.fromJson(java.util.Map.of("name", "Overflow"))));
        org.assertj.core.api.Assertions.assertThat(result).isInstanceOf(com.beehome.shared.exception.InputException.class);
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "select count(*) from beehome.study_subjects where family_id = ?", Integer.class, familyId)).isEqualTo(1000);
    }

    // Hold the first write uncommitted until the second either finishes or waits on a database lock.
    // This forces overlapping transactions without relying on request timing or sleeps.
    private Object overlapWrites(Runnable first, java.util.function.Supplier<Object> second) throws Exception {
        var transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        String application = "study-review-" + UUID.randomUUID();
        try (ExecutorService workers = Executors.newSingleThreadExecutor()) {
            var future = new java.util.concurrent.atomic.AtomicReference<Future<Object>>();
            transactions.executeWithoutResult(tx -> {
                first.run();
                future.set(workers.submit(() -> {
                    try {
                        return transactions.execute(other -> {
                            jdbc.queryForObject("select set_config('application_name', ?, true)", String.class, application);
                            return second.get();
                        });
                    } catch (com.beehome.shared.exception.InputException e) {
                        return e;
                    }
                }));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!future.get().isDone()) {
                    jdbc.execute("select pg_stat_clear_snapshot()");
                    if (jdbc.queryForObject("""
                            select count(*) from pg_stat_activity
                            where application_name = ? and wait_event_type = 'Lock'
                            """, Integer.class, application) > 0) return;
                    if (System.nanoTime() > deadline) throw new AssertionError("Second write neither completed nor reached a database lock");
                }
            });
            return future.get().get(10, TimeUnit.SECONDS);
        }
    }

    @Test void ownerCanVoidOverLimitTimerAndStartAgain() throws Exception {
        UUID owner = user(), reader = user(); String family = family(owner), member = member(owner, family);
        jdbc.update("insert into beehome.family_memberships(id,family_id,user_id,role,created_at) values (?, ?, ?, 'MEMBER', now())",
                UUID.randomUUID(), UUID.fromString(family.substring(family.lastIndexOf('/') + 1)), reader);
        String base = member + "/study-sessions";
        String started = call(owner, post(base + "/start").contentType("application/json").content("{}"));
        String id = com.jayway.jsonpath.JsonPath.read(started, "$.id");
        at("2027-09-21T15:00:01Z");
        mvc.perform(post(base + "/" + id + "/void").with(jwt().jwt(j -> j.subject(reader.toString()))))
                .andExpect(status().isForbidden());
        mvc.perform(post(base + "/" + id + "/finish").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isConflict());
        mvc.perform(post(base + "/" + id + "/void").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VOIDED"))
                .andExpect(jsonPath("$.endedAt").value("2027-09-21T15:00:01Z"))
                .andExpect(jsonPath("$.accumulatedDurationSeconds").value(31536000));
        mvc.perform(get(base + "/current").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isNoContent());
        mvc.perform(post(base + "/start").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{}"))
                .andExpect(status().isCreated());
    }
}
