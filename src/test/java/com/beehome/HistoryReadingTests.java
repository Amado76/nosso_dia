package com.beehome;

import java.util.UUID;
import java.time.*;
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

@SpringBootTest(properties = {"auth.allow-ephemeral-key=true", "app.reports.max-period-days=730", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HistoryReadingTests {
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
        jdbc.update("insert into beehome.users(id,name,email,created_at,updated_at) values (?, 'Reading', ?, now(), now())", user, user + "@example.com");
        return user;
    }
    String call(UUID user, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.with(jwt().jwt(j -> j.subject(user.toString())))).andReturn().getResponse().getContentAsString();
    }
    String family(UUID user) throws Exception {
        String json = call(user, post("/api/families").contentType("application/json")
                .content("{\"name\":\"Reading\",\"timezone\":\"America/Asuncion\"}"));
        return "/api/families/" + com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }
    String member(UUID user, String family) throws Exception {
        String json = call(user, post(family + "/members").contentType("application/json")
                .content("{\"name\":\"Child\",\"memberType\":\"CHILD\"}"));
        return family + "/children/" + com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }

    String create(UUID user, String route, String body) throws Exception {
        String json = mvc.perform(post(route).with(jwt().jwt(j -> j.subject(user.toString())))
                .contentType("application/json").content(body)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(json, "$.id");
    }
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

    @Test void readingOnlyDaysArePagedAndReflectEditsAndDeletes() throws Exception {
        UUID user=user(); String family=family(user), child=member(user,family);
        String book=create(user,family+"/books", "{\"title\":\"A story\"}");
        String journey=create(user,child+"/books", "{\"bookId\":\""+book+"\"}");
        String first=create(user,child+"/reading-sessions", "{\"childBookId\":\""+journey+"\",\"date\":\"2026-09-20\"}");
        at("2026-09-22T15:00:00Z");
        String second=create(user,child+"/reading-sessions", "{\"childBookId\":\""+journey+"\",\"date\":\"2026-09-20\",\"minutes\":12,\"pagesRead\":5}");
        mvc.perform(get(child+"/history/2026-09-20?readingSize=1").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reading.length()").value(1))
                .andExpect(jsonPath("$.reading[0].id").value(second)).andExpect(jsonPath("$.reading[0].bookId").value(book))
                .andExpect(jsonPath("$.reading[0].bookTitle").value("A story"))
                .andExpect(jsonPath("$.reading[0].minutes").value(12)).andExpect(jsonPath("$.reading[0].pagesRead").value(5))
                .andExpect(jsonPath("$.readingHasNext").value(true));
        mvc.perform(get(child+"/history/2026-09-20?readingSize=1&readingPage=1").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reading[0].id").value(first))
                .andExpect(jsonPath("$.reading[0].minutes").isEmpty()).andExpect(jsonPath("$.reading[0].pagesRead").isEmpty())
                .andExpect(jsonPath("$.readingHasNext").value(false));
        mvc.perform(put(child+"/reading-sessions/"+second).with(jwt().jwt(j->j.subject(user.toString())))
                .contentType("application/json").content("{\"date\":\"2026-09-21\",\"minutes\":7}"))
                .andExpect(status().isOk());
        mvc.perform(get(child+"/calendar?year=2026&month=9").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].date").value("2026-09-20"))
                .andExpect(jsonPath("$.days[0].hasReading").value(true));
        mvc.perform(get(child+"/history?from=2026-09-01&to=2026-09-30&size=1").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].date").value("2026-09-21"))
                .andExpect(jsonPath("$.items[0].readingSessions").value(1)).andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(delete(child+"/reading-sessions/"+second).with(jwt().jwt(j->j.subject(user.toString())))).andExpect(status().isNoContent());
        mvc.perform(get(child+"/calendar?year=2026&month=9").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(jsonPath("$.days.length()").value(1)).andExpect(jsonPath("$.days[0].hasReading").value(true));
        mvc.perform(get(child+"/reports?from=2026-09-01&to=2026-09-30").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reading.sessions").value(1))
                .andExpect(jsonPath("$.reading.totalMinutes").value(0)).andExpect(jsonPath("$.reading.pagesRead").value(0));
        for(String query:java.util.List.of("readingSize=101","readingPage=-1","readingPage=2147483647"))
            mvc.perform(get(child+"/history/2026-09-20?"+query).with(jwt().jwt(j->j.subject(user.toString())))).andExpect(status().isBadRequest());
    }

    @Test void reportsCountRereadsOverlapsAndHistoricalCompletionsAcrossConfiguredPeriod() throws Exception {
        UUID user=user(); String family=family(user), child=member(user,family);
        String book=create(user,family+"/books", "{\"title\":\"Again\"}");
        String completed=create(user,child+"/books", "{\"bookId\":\""+book+"\",\"status\":\"COMPLETED\",\"completedOn\":\"2025-01-01\"}");
        String reread=create(user,child+"/books", "{\"bookId\":\""+book+"\",\"status\":\"READING\"}");
        for(String journey:java.util.List.of(completed,reread))
            create(user,child+"/reading-sessions", "{\"childBookId\":\""+journey+"\",\"date\":\"2026-09-21\",\"minutes\":10,\"startPage\":0,\"endPage\":20}");
        mvc.perform(get(child+"/reports?from=2025-01-01&to=2026-09-30").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reading.sessions").value(2))
                .andExpect(jsonPath("$.reading.books").value(1)).andExpect(jsonPath("$.reading.booksCompleted").value(1))
                .andExpect(jsonPath("$.reading.totalMinutes").value(20)).andExpect(jsonPath("$.reading.pagesRead").value(40));
        mvc.perform(get(child+"/reports?from=2025-01-01&to=2025-01-01").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reading.sessions").value(0))
                .andExpect(jsonPath("$.reading.books").value(0)).andExpect(jsonPath("$.reading.booksCompleted").value(1));
        mvc.perform(get(child+"/reports?from=2026-09-01&to=2026-09-30").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(jsonPath("$.reading.booksCompleted").value(0));
        mvc.perform(get(child+"/history?from=2025-01-01&to=2026-09-30").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].readingSessions").value(2));
        mvc.perform(get(child+"/reading-summary?from=2025-01-01&to=2026-09-30").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.books").value(1));
        mvc.perform(get(child+"/reports?from=2024-01-01&to=2026-09-30").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test void readingActivityIsIsolatedAndQueriesStayBounded() throws Exception {
        UUID user=user(), outsider=user(); String family=family(user), child=member(user,family), other=member(user,family);
        String book=create(user,family+"/books", "{\"title\":\"Private\"}");
        String journey=create(user,child+"/books", "{\"bookId\":\""+book+"\"}");
        for(String date:java.util.List.of("2026-09-20","2026-09-21"))
            create(user,child+"/reading-sessions", "{\"childBookId\":\""+journey+"\",\"date\":\""+date+"\"}");
        for(String route:java.util.List.of("/history/2026-09-20","/calendar?year=2026&month=9",
                "/history?from=2026-09-01&to=2026-09-30","/reports?from=2026-09-01&to=2026-09-30")) {
            mvc.perform(get(child+route)).andExpect(status().isUnauthorized());
            mvc.perform(get(child+route).with(jwt().jwt(j->j.subject(outsider.toString())))).andExpect(status().isNotFound());
        }
        mvc.perform(get(other+"/reports?from=2026-09-01&to=2026-09-30").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reading.sessions").value(0));
        mvc.perform(get(other+"/calendar?year=2026&month=9").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days").isEmpty());
        var statistics=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        for(String route:java.util.List.of("/history","/reports")) {
            statistics.clear();
            mvc.perform(get(child+route+"?from=2026-09-21&to=2026-09-21").with(jwt().jwt(j->j.subject(user.toString())))).andExpect(status().isOk());
            long count=statistics.getPrepareStatementCount();
            statistics.clear();
            mvc.perform(get(child+route+"?from=2025-01-01&to=2026-09-30").with(jwt().jwt(j->j.subject(user.toString())))).andExpect(status().isOk());
            org.assertj.core.api.Assertions.assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(count+1);
        }
    }
    @Test void largeReadingDaysAreFullyReachableAndCalendarDoesNotLoadSessions() throws Exception {
        UUID user=user(); String family=family(user), child=member(user,family);
        String book=create(user,family+"/books", "{\"title\":\"Many sessions\"}");
        String journey=create(user,child+"/books", "{\"bookId\":\""+book+"\"}");
        jdbc.update("""
                insert into beehome.reading_sessions
                    (id,family_id,child_id,child_book_id,date,created_by,created_at,updated_at)
                select gen_random_uuid(),?,?,?,'2026-09-21',?,now(),now() from generate_series(1,105)
                """, UUID.fromString(family.substring(family.lastIndexOf('/')+1)),
                UUID.fromString(child.substring(child.lastIndexOf('/')+1)), UUID.fromString(journey), user);
        java.util.Set<String> ids=new java.util.HashSet<>();
        for(int page=0;page<2;page++) {
            String json=mvc.perform(get(child+"/history/2026-09-21?readingSize=100&readingPage="+page)
                    .with(jwt().jwt(j->j.subject(user.toString())))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.reading.length()").value(page==0 ? 100 : 5))
                    .andExpect(jsonPath("$.readingHasNext").value(page==0))
                    .andReturn().getResponse().getContentAsString();
            java.util.List<String> pageIds=com.jayway.jsonpath.JsonPath.read(json,"$.reading[*].id");
            for(String id:pageIds) org.assertj.core.api.Assertions.assertThat(ids.add(id)).isTrue();
        }
        org.assertj.core.api.Assertions.assertThat(ids).hasSize(105);
        var statistics=entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.clear();
        mvc.perform(get(child+"/calendar?year=2026&month=9").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days[0].hasReading").value(true));
        org.assertj.core.api.Assertions.assertThat(statistics.getEntityStatistics("com.beehome.reading.entity.ReadingSession").getLoadCount()).isZero();
        mvc.perform(get(child+"/history?from=2026-09-01&to=2026-09-30").with(jwt().jwt(j->j.subject(user.toString()))))
                .andExpect(jsonPath("$.items[0].readingSessions").value(105));
    }

}
