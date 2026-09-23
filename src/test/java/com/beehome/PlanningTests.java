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
class PlanningTests {
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
    @Test void requiresTimezoneAndSupportsPresenceAwareFamilyPatch() throws Exception {
        UUID u = user();
        call(u, body(post("/api/families"), "{\"name\":\"F\"}")).andExpect(status().isBadRequest());
        for (String zone : new String[]{"+03:00", "UTC+03:00", "Moon/Base", ""}) {
            call(u, body(post("/api/families"), "{\"name\":\"F\",\"timezone\":\"" + zone + "\"}")).andExpect(status().isBadRequest());
        }
        String f = family(u);
        call(u, body(patch(f), "{\"timezone\":\"Europe/Lisbon\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.timezone").value("Europe/Lisbon"));
        call(u, body(patch(f), "{\"name\":\"Renamed\"}")).andExpect(jsonPath("$.timezone").value("Europe/Lisbon"));
        for (String b : new String[]{"{}", "{\"timezone\":null}", "{\"name\":null}"}) call(u, body(patch(f), b)).andExpect(status().isBadRequest());
    }
    @Test void resolvesApplicableActiveItemsAndKeepsReadsSideEffectFree() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), r = routine(u, f);
        String day = f + "/members/" + m + "/daily-plan";
        call(u, get(day).param("date", "2026-09-21")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        assertThat(jdbc.queryForObject("select count(*) from beehome.daily_plans where family_member_id = ?", Integer.class, UUID.fromString(m))).isZero();
        String ri = id(call(u, body(post(r + "/items"), "{\"familyMemberId\":\"" + m + "\",\"title\":\" Breakfast \",\"scheduledTime\":\"08:30\",\"sortOrder\":2}")).andExpect(status().isCreated()));
        call(u, body(put(day + "/2026-09-21"), "{\"note\":\" Remember water \"}")).andExpect(status().isOk());
        String di = id(call(u, body(post(day + "/2026-09-21/items"), "{\"title\":\"Trip\",\"sortOrder\":2}")).andExpect(status().isCreated()));
        call(u, get(day).param("date", "2026-09-21")).andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("America/Asuncion")).andExpect(jsonPath("$.note").value("Remember water"))
            .andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.items[0].source").value("ROUTINE"))
            .andExpect(jsonPath("$.items[0].sourceId").value(ri)).andExpect(jsonPath("$.items[0].scheduledTime").value("08:30"))
            .andExpect(jsonPath("$.items[1].sourceId").value(di)).andExpect(jsonPath("$.items[0].completed").doesNotExist());
        for (String date : new String[]{"2026-09-20", "2026-09-22", "2026-10-05"}) call(u, get(day).param("date", date)).andExpect(jsonPath("$.items.length()").value(0));
        call(u, get(day).param("date", "2026-09-28")).andExpect(jsonPath("$.items.length()").value(1));
        call(u, post(r + "/items/" + ri + "/deactivate")).andExpect(status().isOk());
        call(u, post(day + "/2026-09-21/items/" + di + "/deactivate")).andExpect(status().isOk());
        call(u, get(day).param("date", "2026-09-21")).andExpect(jsonPath("$.items.length()").value(0)).andExpect(jsonPath("$.note").value("Remember water"));
        call(u, post(r + "/items/" + ri + "/reactivate")).andExpect(status().isOk());
        call(u, post(r + "/deactivate")).andExpect(status().isOk());
        call(u, get(day).param("date", "2026-09-28")).andExpect(jsonPath("$.items.length()").value(0));
    }
    String routineItem(UUID u, String r, String m, int order) throws Exception {
        return id(call(u, body(post(r + "/items"), "{\"familyMemberId\":\"" + m + "\",\"title\":\"Item\",\"sortOrder\":" + order + "}")).andExpect(status().isCreated()));
    }
    String dailyItem(UUID u, String day, int order) throws Exception {
        return id(call(u, body(post(day + "/items"), "{\"title\":\"Extra\",\"sortOrder\":" + order + "}")).andExpect(status().isCreated()));
    }
    void membership(String family, UUID u, String role) {
        jdbc.update("insert into beehome.family_memberships(id,family_id,user_id,role,created_at) values (?, ?, ?, ?, now())",
                UUID.randomUUID(), UUID.fromString(family.substring(family.lastIndexOf('/') + 1)), u, role);
    }
    @Test void authorizesEveryRouteAndScopesNestedResources() throws Exception {
        UUID owner = user(), reader = user(), admin = user(), outsider = user();
        String f = family(owner), m = member(owner, f), r = routine(owner, f), ri = routineItem(owner, r, m, 0);
        String dp = f + "/members/" + m + "/daily-plan", day = dp + "/2026-09-21", di = dailyItem(owner, day, 0);
        membership(f, reader, "MEMBER"); membership(f, admin, "ADMIN");
        for (String url : new String[]{f + "/routines", r, dp + "?date=2026-09-21"}) {
            mvc.perform(get(url)).andExpect(status().isUnauthorized());
            call(reader, get(url)).andExpect(status().isOk());
            call(outsider, get(url)).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAMILY_NOT_FOUND"));
        }
        for (var request : java.util.List.of(
                body(post(f + "/routines"), "{\"name\":\"R\",\"daysOfWeek\":[\"MONDAY\"]}"),
                body(patch(r), "{\"name\":\"R\"}"), post(r + "/deactivate"), post(r + "/reactivate"),
                body(post(r + "/items"), "{\"familyMemberId\":\"" + m + "\",\"title\":\"I\",\"sortOrder\":1}"),
                body(patch(r + "/items/" + ri), "{\"title\":\"I\"}"),
                post(r + "/items/" + ri + "/deactivate"), post(r + "/items/" + ri + "/reactivate"),
                body(put(r + "/items/order"), "{\"items\":[{\"id\":\"" + ri + "\",\"sortOrder\":0}]}"),
                body(put(day), "{\"note\":\"Note\"}"), body(post(day + "/items"), "{\"title\":\"I\",\"sortOrder\":1}"),
                body(patch(day + "/items/" + di), "{\"title\":\"I\"}"), post(day + "/items/" + di + "/deactivate"),
                post(day + "/items/" + di + "/reactivate"),
                body(put(day + "/items/order"), "{\"items\":[{\"id\":\"" + di + "\",\"sortOrder\":0}]}"))) {
            call(reader, request).andExpect(status().isForbidden());
            call(outsider, request).andExpect(status().isNotFound());
            mvc.perform(request.with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous())).andExpect(status().isUnauthorized());
        }
        call(admin, body(patch(r), "{\"name\":\"Admin edited\"}")).andExpect(status().isOk());
        call(admin, body(put(day), "{\"note\":\"Admin note\"}")).andExpect(status().isOk());
        String other = family(owner), otherMember = member(owner, other), otherRoutine = routine(owner, other);
        call(owner, body(post(r + "/items"), "{\"familyMemberId\":\"" + otherMember + "\",\"title\":\"I\",\"sortOrder\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("FAMILY_MEMBER_NOT_FOUND"));
        call(owner, get(other + r.substring(f.length()))).andExpect(status().isNotFound());
        call(owner, body(patch(otherRoutine + "/items/" + ri), "{\"title\":\"I\"}")).andExpect(status().isNotFound());
        call(owner, body(patch(dp + "/2026-09-22/items/" + di), "{\"title\":\"I\"}")).andExpect(status().isNotFound());
        String m2 = member(owner, f), day2 = f + "/members/" + m2 + "/daily-plan/2026-09-21";
        dailyItem(owner, day2, 0);
        call(owner, post(day2 + "/items/" + di + "/deactivate")).andExpect(status().isNotFound());
        call(owner, body(put(r + "/items/order"), "{\"items\":[{\"id\":\"" + UUID.randomUUID() + "\",\"sortOrder\":0}]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROUTINE_ITEM_NOT_FOUND"));
    }
    @Test void validatesPartialEditsReordersAndInactiveMembers() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), r = routine(u, f);
        for (String b : new String[]{"{}", "{\"name\":null}", "{\"daysOfWeek\":[]}", "{\"daysOfWeek\":null}",
                "{\"daysOfWeek\":[\"monday\"]}", "{\"daysOfWeek\":[\"MONDAY\",\"MONDAY\"]}", "{\"endDate\":\"2026-09-20\"}", "{\"active\":false}"}) {
            call(u, body(patch(r), b)).andExpect(status().isBadRequest());
        }
        call(u, body(patch(r), "{\"startDate\":null,\"endDate\":null}")).andExpect(status().isOk()).andExpect(jsonPath("$.startDate").value(org.hamcrest.Matchers.nullValue()));
        String a = routineItem(u, r, m, 0), b = routineItem(u, r, m, 1);
        call(u, post(r + "/items/" + b + "/deactivate")).andExpect(status().isOk());
        call(u, body(put(r + "/items/order"), "{\"items\":[{\"id\":\"" + a + "\",\"sortOrder\":1},{\"id\":\"" + b + "\",\"sortOrder\":0}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(b));
        call(u, body(put(r + "/items/order"), "{\"items\":[{\"id\":\"" + a + "\",\"sortOrder\":0}]}")).andExpect(status().isBadRequest());
        String dp = f + "/members/" + m + "/daily-plan", day = dp + "/2026-09-21";
        String c = dailyItem(u, day, 0), d = dailyItem(u, day, 1);
        call(u, post(day + "/items/" + d + "/deactivate")).andExpect(status().isOk());
        call(u, body(put(day + "/items/order"), "{\"items\":[{\"id\":\"" + c + "\",\"sortOrder\":1},{\"id\":\"" + d + "\",\"sortOrder\":0}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(d));
        call(u, body(post(day + "/items"), "{\"title\":\"Duplicate order\",\"sortOrder\":0}")).andExpect(status().isBadRequest());
        call(u, body(patch(day + "/items/" + c), "{\"sortOrder\":0}")).andExpect(status().isBadRequest());
        call(u, body(patch(day + "/items/" + c), "{\"description\":\"  text  \",\"scheduledTime\":\"09:15\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value("text"));
        call(u, body(patch(day + "/items/" + c), "{\"description\":null,\"scheduledTime\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()));
        for (String field : new String[]{"title", "sortOrder"}) call(u, body(patch(day + "/items/" + c), "{\"" + field + "\":null}")).andExpect(status().isBadRequest());
        for (String value : new String[]{"8:30","08:30:00","24:00","08:30Z"}) call(u, body(patch(day + "/items/" + c), "{\"scheduledTime\":\"" + value + "\"}")).andExpect(status().isBadRequest());
        for (String date : new String[]{"2026-02-30","2026-9-21","", "2026-09-21T00:00:00Z"}) call(u, get(dp).param("date", date)).andExpect(status().isBadRequest());
        call(u, get(dp)).andExpect(status().isBadRequest());
        call(u, post(f + "/members/" + m + "/deactivate")).andExpect(status().isOk());
        call(u, get(dp).param("date", "2026-09-21")).andExpect(status().isNotFound());
        call(u, body(put(day), "{\"note\":\"Blocked\"}")).andExpect(status().isNotFound());
        call(u, body(patch(r + "/items/" + a), "{\"title\":\"Blocked\"}")).andExpect(status().isNotFound());
    }
    @Test void reloadsCancelledDailyItemsForReorderingAndRestoration() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), dp = f + "/members/" + m + "/daily-plan";
        String day = dp + "/2026-09-21";
        String active = dailyItem(u, day, 0), cancelled = dailyItem(u, day, 1);
        call(u, post(day + "/items/" + cancelled + "/deactivate")).andExpect(status().isOk());
        call(u, get(dp).param("date", "2026-09-21")).andExpect(jsonPath("$.items.length()").value(1));
        String reloaded = call(u, get(day + "/items")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(active))
                .andExpect(jsonPath("$[1].active").value(false))
                .andReturn().getResponse().getContentAsString();
        String restoredId = com.jayway.jsonpath.JsonPath.read(reloaded, "$[1].id");
        call(u, body(put(day + "/items/order"), "{\"items\":[{\"id\":\"" + active
                + "\",\"sortOrder\":1},{\"id\":\"" + restoredId + "\",\"sortOrder\":0}]}"))
                .andExpect(status().isOk());
        call(u, post(day + "/items/" + restoredId + "/reactivate")).andExpect(status().isOk());
        call(u, get(dp).param("date", "2026-09-21"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].sourceId").value(cancelled));
    }

    @Test void scopesDailyManagementReadsAndDoesNotCreateEmptyPlans() throws Exception {
        UUID owner = user(), reader = user(), outsider = user();
        String f = family(owner), m = member(owner, f), day = f + "/members/" + m + "/daily-plan/2026-09-21";
        membership(f, reader, "MEMBER");
        call(reader, get(day + "/items")).andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThat(jdbc.queryForObject("select count(*) from beehome.daily_plans where family_member_id = ?",
                Integer.class, UUID.fromString(m))).isZero();
        dailyItem(owner, day, 0);
        call(reader, get(day + "/items")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(day + "/items")).andExpect(status().isUnauthorized());
        call(outsider, get(day + "/items")).andExpect(status().isNotFound());
        String other = family(owner);
        call(owner, get(other + "/members/" + m + "/daily-plan/2026-09-21/items")).andExpect(status().isNotFound());
        call(owner, get(f + "/members/" + m + "/daily-plan/2026-09-22/items"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        call(owner, get(f + "/members/" + m + "/daily-plan/2026-02-30/items")).andExpect(status().isBadRequest());
        call(owner, post(f + "/members/" + m + "/deactivate")).andExpect(status().isOk());
        call(owner, get(day + "/items")).andExpect(status().isNotFound());
    }

    @Test void doesNotPersistEmptyNotesAndConvergesConcurrentFirstWrites() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), dp = f + "/members/" + m + "/daily-plan";
        call(u, body(put(dp + "/2026-09-21"), "{\"note\":\"  \"}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from beehome.daily_plans where family_member_id = ?", Integer.class, UUID.fromString(m))).isZero();
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 4; i++) {
                int order = i;
                futures.add(pool.submit(() -> { start.await(); dailyItem(u, dp + "/2026-09-21", order); return null; }));
            }
            start.countDown();
            for (var future : futures) future.get(20, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("select count(*) from beehome.daily_plans where family_member_id = ?", Integer.class, UUID.fromString(m))).isEqualTo(1);
        call(u, get(dp).param("date", "2026-09-21")).andExpect(jsonPath("$.items.length()").value(4));
        call(u, body(put(dp + "/2026-09-22"), "{\"note\":\"Tomorrow\"}")).andExpect(status().isOk());
        call(u, body(put(dp + "/2026-09-22"), "{\"note\":null}")).andExpect(status().isOk()).andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.nullValue()));
        call(u, get(dp).param("date", "2026-09-21")).andExpect(jsonPath("$.items.length()").value(4));
    }
    @Test void paginatesFiltersAndPreservesAuditOnNoOps() throws Exception {
        UUID u = user(); String f = family(u), r = routine(u, f);
        String unchanged = call(u, get(r)).andReturn().getResponse().getContentAsString();
        call(u, body(patch(r), "{\"name\":\" Morning \"}")).andExpect(status().isOk()).andExpect(content().json(unchanged));
        call(u, post(r + "/reactivate")).andExpect(status().isOk()).andExpect(content().json(unchanged));
        routine(u, f);
        call(u, get(f + "/routines").param("size", "1")).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.hasNext").value(true));
        call(u, post(r + "/deactivate")).andExpect(status().isOk());
        call(u, get(f + "/routines")).andExpect(jsonPath("$.items.length()").value(1));
        call(u, get(f + "/routines").param("includeInactive", "true")).andExpect(jsonPath("$.items.length()").value(2));
        for (String q : new String[]{"?page=-1","?size=101","?size=0","?includeInactive=yes","?page=2147483647&size=100"}) call(u, get(f + "/routines" + q)).andExpect(status().isBadRequest());
    }
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @Test void resolvesWithConstantQueryCountAndDeterministicTies() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), r = routine(u, f);
        String a = routineItem(u, r, m, 0), b = routineItem(u, r, m, 0);
        String dp = f + "/members/" + m + "/daily-plan", day = dp + "/2026-09-21";
        dailyItem(u, day, 0);
        var stats = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        try {
            stats.clear();
            var expected = java.util.stream.Stream.of(a, b).sorted().toList();
            call(u, get(dp).param("date", "2026-09-21")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items[0].sourceId").value(expected.get(0)))
                    .andExpect(jsonPath("$.items[1].sourceId").value(expected.get(1)));
            long baseline = stats.getPrepareStatementCount();
            for (int i = 0; i < 8; i++) routineItem(u, routine(u, f), m, i);
            stats.clear();
            call(u, get(dp).param("date", "2026-09-21")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(11));
            assertThat(stats.getPrepareStatementCount()).isEqualTo(baseline).isLessThanOrEqualTo(10);
        } finally { stats.setStatisticsEnabled(false); }
    }
    @Test void documentsSecurityPresenceAndTimeAndLocalizesMissingResources() throws Exception {
        mvc.perform(get("/v3/api-docs").with(jwt())).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/families/{familyId}/routines'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/families/{familyId}/routines'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/families/{familyId}/members/{memberId}/daily-plan'].get.parameters[?(@.name == 'date')].required").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.paths['/api/families/{familyId}/routines'].get.responses['404'].content['application/problem+json'].schema['$ref']").value("#/components/schemas/ProblemDetail"))
                .andExpect(jsonPath("$.components.schemas.PatchRoutineRequest.properties.fields").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CreateFamilyRequest.required").value(org.hamcrest.Matchers.hasItems("name", "timezone")))
                .andExpect(jsonPath("$.components.schemas.RoutineItemResponse.properties.scheduledTime.example").value("08:30"));
        UUID u = user(); String f = family(u);
        call(u, get(f + "/routines/" + UUID.randomUUID()).header("Accept-Language", "pt-BR"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Rotina não encontrada."));
        call(u, get(f + "/routines/" + UUID.randomUUID()).header("Accept-Language", "es"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.detail").value("Rutina no encontrada."));
    }
    @Test void databaseEnforcesFamilyReferencesNonemptyDaysAndUniquePlans() throws Exception {
        UUID u = user(); String f = family(u), m = member(u, f), r = routine(u, f), other = family(u), otherM = member(u, other);
        UUID rid = UUID.fromString(r.substring(r.lastIndexOf('/') + 1));
        UUID fid = UUID.fromString(f.substring(f.lastIndexOf('/') + 1));
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            jdbc.update("insert into beehome.routine_items(id,family_id,routine_id,family_member_id,title,sort_order,created_at,updated_at) values (?, ?, ?, ?, 'Bad', 0, now(), now())", UUID.randomUUID(), fid, rid, UUID.fromString(otherM))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            jdbc.update("delete from beehome.routine_days where routine_id = ?", rid)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        String day = f + "/members/" + m + "/daily-plan/2026-09-21";
        dailyItem(u, day, 0);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            jdbc.update("insert into beehome.daily_plans(id,family_id,family_member_id,plan_date,created_at,updated_at) values (?, ?, ?, '2026-09-21', now(), now())", UUID.randomUUID(), fid, UUID.fromString(m))))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        UUID plan = jdbc.queryForObject("select id from beehome.daily_plans where family_member_id = ?", UUID.class, UUID.fromString(m));
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
            jdbc.update("insert into beehome.daily_plan_items(id,daily_plan_id,title,sort_order,created_at,updated_at) values (?, ?, 'Bad', 0, now(), now())", UUID.randomUUID(), plan)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
