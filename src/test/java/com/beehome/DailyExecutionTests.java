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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "auth.allow-ephemeral-key=true")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DailyExecutionTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    UUID user() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into beehome.users(id,name,email,created_at,updated_at) values (?, 'Planner', ?, now(), now())", id, id + "@example.com");
        return id;
    }
    ResultActions call(UUID u, MockHttpServletRequestBuilder r) throws Exception {
        return mvc.perform(r.with(jwt().jwt(j -> j.subject(u.toString()))));
    }
    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder r, String b) { return r.contentType("application/json").content(b); }
    String id(ResultActions r) throws Exception { return com.jayway.jsonpath.JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.id"); }
    String family(UUID u) throws Exception {
        return "/api/families/" + id(call(u, body(post("/api/families"), "{\"name\":\"Family\",\"timezone\":\"America/Asuncion\"}")).andExpect(status().isCreated()));
    }
    String member(UUID u, String f) throws Exception {
        return id(call(u, body(post(f + "/members"), "{\"name\":\"Child\",\"memberType\":\"CHILD\"}")).andExpect(status().isCreated()));
    }
    String routine(UUID u, String f) throws Exception {
        return f + "/routines/" + id(call(u, body(post(f + "/routines"), "{\"name\":\" Morning \",\"daysOfWeek\":[\"MONDAY\"],\"startDate\":\"2026-09-21\",\"endDate\":\"2026-09-28\"}")).andExpect(status().isCreated()));
    }
    @org.springframework.test.context.bean.override.mockito.MockitoBean java.time.Clock clock;
    @org.junit.jupiter.api.BeforeEach void time() {
        org.mockito.Mockito.when(clock.instant()).thenReturn(java.time.Instant.parse("2026-09-21T15:00:00Z"));
        org.mockito.Mockito.when(clock.withZone(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> java.time.Clock.fixed(clock.instant(), i.getArgument(0)));
    }
    String today() { return "2026-09-21"; }
    String itemId(ResultActions r) throws Exception { return com.jayway.jsonpath.JsonPath.read(r.andReturn().getResponse().getContentAsString(), "$.items[0].id"); }
    String daily(UUID u, String base, String title, int order) throws Exception {
        return id(call(u, body(post(base + "/daily-plan/" + today() + "/items"), "{\"title\":\"" + title + "\",\"sortOrder\":" + order + "}")).andExpect(status().isCreated()));
    }
    void role(UUID user, String family, String role) {
        jdbc.update("insert into beehome.family_memberships(id,family_id,user_id,role,created_at) values (?, ?, ?, ?, now())",
            UUID.randomUUID(), UUID.fromString(family.substring(family.lastIndexOf('/') + 1)), user, role);
    }

    @Test void materializesSnapshotsAndCompletesIdempotently() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f);
        String base = f + "/members/" + m, day = base + "/executions/" + today();
        call(u, body(post(base + "/daily-plan/" + today() + "/items"), "{\"title\":\"Breakfast\",\"sortOrder\":0}" )).andExpect(status().isCreated());
        call(u, get(day)).andExpect(status().isNotFound());
        String result = call(u, put(day)).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].title").value("Breakfast"))
            .andExpect(jsonPath("$.items[0].status").value("PENDING"))
            .andExpect(jsonPath("$.summary.pending").value(1)).andReturn().getResponse().getContentAsString();
        call(u, put(day)).andExpect(content().json(result));
        String item = com.jayway.jsonpath.JsonPath.read(result, "$.items[0].id");
        String completed = call(u, post(day + "/items/" + item + "/complete")).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].completedByUserId").value(u.toString()))
            .andExpect(jsonPath("$.summary.completionPercentage").value(100.0)).andReturn().getResponse().getContentAsString();
        call(u, post(day + "/items/" + item + "/complete")).andExpect(content().json(completed));
        call(u, post(day + "/items/" + item + "/uncomplete")).andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].completedAt").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void synchronizesPendingSourcesButPreservesCompletedSnapshotsAndReopenedHistory() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), base = f + "/members/" + m, day = base + "/executions/" + today();
        String a = daily(u, base, "First", 0), b = daily(u, base, "Second", 1);
        String completed = itemId(call(u, put(day)).andExpect(status().isOk()));
        call(u, post(day + "/items/" + completed + "/complete")).andExpect(status().isOk());
        String plan = base + "/daily-plan/" + today();
        call(u, body(patch(plan + "/items/" + a), "{\"title\":\"Changed completed\"}")).andExpect(status().isOk());
        call(u, body(patch(plan + "/items/" + b), "{\"title\":\"Changed pending\",\"description\":\"Details\",\"scheduledTime\":\"09:30\"}")).andExpect(status().isOk());
        call(u, body(put(plan), "{\"note\":\"Remember\"}")).andExpect(status().isOk());
        call(u, put(day)).andExpect(jsonPath("$.items[0].title").value("First"))
            .andExpect(jsonPath("$.items[1].title").value("Changed pending"))
            .andExpect(jsonPath("$.items[1].scheduledTime").value("09:30")).andExpect(jsonPath("$.note").value("Remember"));
        call(u, post(plan + "/items/" + a + "/deactivate")).andExpect(status().isOk());
        call(u, post(plan + "/items/" + b + "/deactivate")).andExpect(status().isOk());
        var cancelled = call(u, put(day)).andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.items[1].status").value("CANCELLED")).andExpect(jsonPath("$.summary.completionPercentage").value(100.0));
        String cancelledId = com.jayway.jsonpath.JsonPath.read(cancelled.andReturn().getResponse().getContentAsString(), "$.items[1].id");
        call(u, post(day + "/items/" + cancelledId + "/complete")).andExpect(status().isConflict());
        call(u, post(plan + "/items/" + b + "/reactivate")).andExpect(status().isOk());
        call(u, put(day)).andExpect(jsonPath("$.items[1].id").value(cancelledId)).andExpect(jsonPath("$.items[1].status").value("PENDING"));
        daily(u, base, "Added", 2);
        call(u, put(day)).andExpect(jsonPath("$.items.length()").value(3));
        String frozen = call(u, post(day + "/finalize")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FINALIZED"))
            .andReturn().getResponse().getContentAsString();
        daily(u, base, "After finalization", 3);
        call(u, put(day)).andExpect(content().json(frozen));
        call(u, post(day + "/items/" + completed + "/uncomplete")).andExpect(status().isConflict());
        call(u, post(day + "/reopen")).andExpect(status().isOk()).andExpect(jsonPath("$.reopened").value(true));
        call(u, put(day)).andExpect(jsonPath("$.items.length()").value(3)).andExpect(jsonPath("$.items[0].title").value("First"));
        call(u, post(day + "/items/" + completed + "/uncomplete")).andExpect(status().isOk());
        call(u, post(day + "/finalize")).andExpect(status().isOk());
    }

    @Test void closesPastDaysWithoutMutatingReadsAndAllowsExplicitCorrections() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), base = f + "/members/" + m, day = base + "/executions/" + today();
        daily(u, base, "Snapshot", 0);
        String item = itemId(call(u, put(day)).andExpect(status().isOk()));
        org.mockito.Mockito.when(clock.instant()).thenReturn(java.time.Instant.parse("2026-09-22T02:59:59Z"));
        call(u, get(day)).andExpect(jsonPath("$.status").value("OPEN"));
        call(u, put(base + "/executions/2026-09-22")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("DAILY_EXECUTION_FUTURE_DATE"));
        org.mockito.Mockito.when(clock.instant()).thenReturn(java.time.Instant.parse("2026-09-22T03:00:00Z"));
        call(u, get(day)).andExpect(jsonPath("$.status").value("FINALIZED")).andExpect(jsonPath("$.finalizedAt").value(org.hamcrest.Matchers.nullValue()));
        assertThat(jdbc.queryForObject("select status from beehome.daily_executions where family_member_id = ?", String.class, UUID.fromString(m))).isEqualTo("OPEN");
        call(u, post(day + "/items/" + item + "/complete")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DAILY_EXECUTION_FINALIZED"));
        assertThat(jdbc.queryForObject("select status from beehome.daily_executions where family_member_id = ?", String.class, UUID.fromString(m))).isEqualTo("FINALIZED");
        call(u, post(day + "/reopen")).andExpect(status().isOk());
        call(u, post(day + "/items/" + item + "/complete")).andExpect(status().isOk());
        call(u, put(day)).andExpect(jsonPath("$.status").value("OPEN"));
        call(u, post(day + "/finalize")).andExpect(status().isOk());
        call(u, put(base + "/executions/2026-09-20")).andExpect(status().isNotFound());
    }

    @Test void checksEveryRouteForMembershipAndScopesItemsAndMembers() throws Exception {
        UUID owner = user(), reader = user(), admin = user(), outsider = user();
        String f = family(owner), m = member(owner, f), base = f + "/members/" + m, day = base + "/executions/" + today();
        role(reader, f, "MEMBER"); role(admin, f, "ADMIN");
        daily(owner, base, "Task", 0);
        String item = itemId(call(reader, put(day)).andExpect(status().isOk()));
        for (var request : java.util.List.of(get(day), put(day), post(day + "/items/" + item + "/complete"),
                post(day + "/items/" + item + "/uncomplete"), post(day + "/finalize"), post(day + "/reopen"),
                get(base + "/executions?from=2026-09-01&to=2026-09-30"))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
            call(outsider, request).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAMILY_NOT_FOUND"));
        }
        call(reader, post(day + "/items/" + item + "/complete")).andExpect(status().isOk());
        call(reader, post(day + "/items/" + item + "/uncomplete")).andExpect(status().isOk());
        call(reader, post(day + "/finalize")).andExpect(status().isForbidden());
        call(admin, post(day + "/finalize")).andExpect(status().isOk());
        call(reader, post(day + "/reopen")).andExpect(status().isForbidden());
        call(admin, post(day + "/reopen")).andExpect(status().isOk());
        String otherFamily = family(owner), otherMember = member(owner, f), otherDay = f + "/members/" + otherMember + "/executions/" + today();
        call(owner, put(otherDay)).andExpect(status().isOk());
        call(owner, post(otherDay + "/items/" + item + "/complete")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DAILY_EXECUTION_ITEM_NOT_FOUND"));
        call(owner, get(otherFamily + "/members/" + m + "/executions/" + today())).andExpect(status().isNotFound());
    }

    @Test void pagesHistoryAndKeepsInactiveMembersReadableWithoutCreatingEmptyDays() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), base = f + "/members/" + m, history = base + "/executions";
        call(u, put(history + "/" + today())).andExpect(status().isOk()).andExpect(jsonPath("$.summary.total").value(0))
            .andExpect(jsonPath("$.summary.completionPercentage").value(org.hamcrest.Matchers.nullValue()));
        org.mockito.Mockito.when(clock.instant()).thenReturn(java.time.Instant.parse("2026-09-22T15:00:00Z"));
        call(u, put(history + "/2026-09-22")).andExpect(status().isOk());
        call(u, get(history).param("from", "2026-09-01").param("to", "2026-09-30").param("size", "1"))
            .andExpect(jsonPath("$.items[0].date").value("2026-09-22")).andExpect(jsonPath("$.hasNext").value(true));
        call(u, get(history).param("from", "2026-09-01").param("to", "2026-09-30").param("size", "1").param("page", "1"))
            .andExpect(jsonPath("$.items[0].date").value(today())).andExpect(jsonPath("$.items[0].status").value("FINALIZED"))
            .andExpect(jsonPath("$.items[0].summary.total").value(0)).andExpect(jsonPath("$.hasNext").value(false));
        call(u, post(base + "/deactivate")).andExpect(status().isOk());
        call(u, get(history + "/" + today())).andExpect(status().isOk());
        call(u, put(history + "/" + today())).andExpect(status().isOk());
        call(u, get(history).param("from", today()).param("to", today())).andExpect(jsonPath("$.items.length()").value(1));
        org.mockito.Mockito.when(clock.instant()).thenReturn(java.time.Instant.parse("2026-09-23T15:00:00Z"));
        call(u, put(history + "/2026-09-23")).andExpect(status().isNotFound());
        for (String query : new String[]{"", "?from=2026-09-30&to=2026-09-01", "?from=2026-02-30&to=2026-09-30", "?from=2026-09-01&to=2026-09-30&size=101", "?from=2026-09-01&to=2026-09-30&page=-1", "?from=2026-09-01&to=2026-09-30&page=2147483647&size=100"}) {
            call(u, get(history + query)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("select count(*) from beehome.daily_executions where family_member_id = ?", Integer.class, UUID.fromString(m))).isEqualTo(2);
    }

    @Test void concurrentCreationAndCompletionConverge() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), base = f + "/members/" + m, day = base + "/executions/" + today();
        daily(u, base, "Concurrent", 0);
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 4; i++) futures.add(pool.submit(() -> { start.await(); return itemId(call(u, put(day)).andExpect(status().isOk())); }));
            start.countDown();
            var ids = new java.util.HashSet<String>();
            for (var future : futures) ids.add(future.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(ids).hasSize(1);
            var completed = new java.util.ArrayList<java.util.concurrent.Future<String>>();
            for (int i = 0; i < 4; i++) completed.add(pool.submit(() -> call(u, post(day + "/items/" + ids.iterator().next() + "/complete"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()));
            var responses = new java.util.HashSet<String>();
            for (var future : completed) responses.add(future.get(20, java.util.concurrent.TimeUnit.SECONDS));
            assertThat(responses).hasSize(1);
        }
        assertThat(jdbc.queryForObject("select count(*) from beehome.daily_executions where family_member_id = ?", Integer.class, UUID.fromString(m))).isEqualTo(1);
    }
    @Test void retainsExistingTodayForInactiveMembersAndUsesCurrentColor() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), base = f + "/members/" + m, day = base + "/executions/" + today();
        daily(u, base, "Keep", 0);
        call(u, put(day)).andExpect(status().isOk());
        call(u, post(day + "/finalize")).andExpect(status().isOk());
        call(u, body(patch(base), "{\"color\":\"#123456\"}")).andExpect(status().isOk());
        call(u, post(base + "/deactivate")).andExpect(status().isOk());
        call(u, get(day)).andExpect(jsonPath("$.member.color").value("#123456"));
        call(u, put(day)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].title").value("Keep"));
    }
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.beehome.dailyexecution.repository.DailyExecutionRepository executionRepository;

    @Test void returnsSafeLocalizedConflictWhenDatabaseCannotAcquireExecutionLock() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), day = f + "/members/" + m + "/executions/" + today();
        call(u, put(day)).andExpect(status().isOk());
        org.mockito.Mockito.doThrow(new org.springframework.dao.CannotAcquireLockException("private database details"))
            .when(executionRepository).lock(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        call(u, post(day + "/finalize").header("Accept-Language", "pt-BR")).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DAILY_EXECUTION_CONFLICT"))
            .andExpect(jsonPath("$.detail").value("O estado da execução impede esta operação. Recarregue antes de tentar novamente."));
    }
    @Test void snapshotsRoutineSourcesAndKeepsHistoricalSummaryIndependentOfPlanning() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), r = routine(u, f), base = f + "/members/" + m;
        String ri = id(call(u, body(post(r + "/items"), "{\"familyMemberId\":\"" + m + "\",\"title\":\"Routine snapshot\",\"sortOrder\":0}")).andExpect(status().isCreated()));
        String day = base + "/executions/" + today();
        String item = itemId(call(u, put(day)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].sourceType").value("ROUTINE"))
            .andExpect(jsonPath("$.items[0].sourceId").value(ri)));
        call(u, post(day + "/items/" + item + "/complete")).andExpect(status().isOk());
        daily(u, base, "Pending", 0);
        call(u, put(day)).andExpect(jsonPath("$.items[0].sourceType").value("ROUTINE")).andExpect(jsonPath("$.summary.completionPercentage").value(50.0));
        call(u, post(r + "/deactivate")).andExpect(status().isOk());
        call(u, put(day)).andExpect(jsonPath("$.items[0].title").value("Routine snapshot"));
        call(u, post(day + "/finalize")).andExpect(status().isOk());
        jdbc.update("delete from beehome.routine_items where id = ?", UUID.fromString(ri));
        call(u, get(day)).andExpect(jsonPath("$.items[0].sourceId").value(ri));
        call(u, get(base + "/executions").param("from", today()).param("to", today()))
            .andExpect(jsonPath("$.items[0].summary.completed").value(1)).andExpect(jsonPath("$.items[0].summary.pending").value(1))
            .andExpect(jsonPath("$.items[0].summary.completionPercentage").value(50.0));
    }

    @Test void enforcesDatabaseIdentityReferencesAndStateConstraints() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), base = f + "/members/" + m, day = base + "/executions/" + today();
        daily(u, base, "Invariant", 0);
        String item = itemId(call(u, put(day)).andExpect(status().isOk()));
        UUID execution = jdbc.queryForObject("select id from beehome.daily_executions where family_member_id = ?", UUID.class, UUID.fromString(m));
        String other = family(u); UUID otherId = UUID.fromString(other.substring(other.lastIndexOf('/') + 1));
        for (String sql : new String[]{
            "insert into beehome.daily_executions select gen_random_uuid(),family_id,family_member_id,execution_date,status,reopened,note,finalized_at,created_at,updated_at from beehome.daily_executions where id = ?",
            "update beehome.daily_executions set status = 'INVALID' where id = ?",
            "update beehome.daily_executions set status = 'FINALIZED' where id = ?",
            "update beehome.daily_executions set family_id = '" + otherId + "' where id = ?"
        }) org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(sql, execution)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        for (String sql : new String[]{
            "insert into beehome.daily_execution_items select gen_random_uuid(),execution_id,source_type,source_id,title,description,scheduled_time,sort_order,status,completed_at,completed_by_user_id,created_at,updated_at from beehome.daily_execution_items where id = ?",
            "update beehome.daily_execution_items set status = 'INVALID' where id = ?",
            "update beehome.daily_execution_items set status = 'COMPLETED' where id = ?",
            "update beehome.daily_execution_items set completed_by_user_id = gen_random_uuid() where id = ?",
            "update beehome.daily_execution_items set execution_id = gen_random_uuid() where id = ?",
            "update beehome.daily_execution_items set source_type = 'INVALID' where id = ?"
        }) org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(sql, UUID.fromString(item))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void boundsAccumulatedSnapshotsAndRollsBackSynchronizationAtTheLimit() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), base = f + "/members/" + m, day = base + "/executions/" + today();
        UUID execution = UUID.fromString(id(call(u, put(day)).andExpect(status().isOk())));
        jdbc.update("insert into beehome.daily_execution_items(id,execution_id,source_type,source_id,title,sort_order,status,created_at,updated_at) select gen_random_uuid(),?,'DAILY_PLAN',gen_random_uuid(),'Old',n,'CANCELLED',now(),now() from generate_series(1,2000) n", execution);
        daily(u, base, "New", 0);
        call(u, put(day)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        assertThat(jdbc.queryForObject("select count(*) from beehome.daily_execution_items where execution_id = ?", Integer.class, execution)).isEqualTo(2000);
    }

    @Test void documentsExecutionSchemaAndLocalizesMissingHistory() throws Exception {
        mvc.perform(get("/v3/api-docs").with(jwt())).andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/families/{familyId}/members/{memberId}/executions/{date}'].put.security[0].bearerAuth").isArray())
            .andExpect(jsonPath("$.paths['/api/families/{familyId}/members/{memberId}/executions/{date}'].put.responses['409']").exists())
            .andExpect(jsonPath("$.paths['/api/families/{familyId}/members/{memberId}/executions'].get.parameters[?(@.name == 'from')].required").value(org.hamcrest.Matchers.contains(true)));
        UUID u = user(); String f = family(u), m = member(u, f), day = f + "/members/" + m + "/executions/" + today();
        call(u, get(day).header("Accept-Language", "es")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Ejecución diaria no encontrada."));
        call(u, get(day).header("Accept-Language", "pt")).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Execução diária não encontrada."));
    }
}
