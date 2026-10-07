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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"auth.allow-ephemeral-key=true", "spring.jpa.properties.hibernate.generate_statistics=true"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HistoryTests {
    private static final java.nio.file.Path storage = tempStorage();
    private static java.nio.file.Path tempStorage() {
        try { return java.nio.file.Files.createTempDirectory("beehome-history-tests-"); }
        catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }
    @DynamicPropertySource static void storage(DynamicPropertyRegistry properties) {
        properties.add("media.storage-directory", storage::toString);
    }
    @org.junit.jupiter.api.AfterAll static void cleanStorage() throws Exception {
        try (var paths = java.nio.file.Files.walk(storage)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.deleteIfExists(path);
        }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.beehome.history.pdf.DailyReportPdfRenderer pdfRenderer;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    @org.springframework.test.context.bean.override.mockito.MockitoBean java.time.Clock clock;
    @org.junit.jupiter.api.BeforeEach void time() {
        org.mockito.Mockito.when(clock.instant()).thenReturn(java.time.Instant.parse("2026-09-21T15:00:00Z"));
        org.mockito.Mockito.when(clock.withZone(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(i -> java.time.Clock.fixed(clock.instant(), i.getArgument(0)));
    }

    private String child(UUID owner) throws Exception {
        jdbc.update("insert into beehome.users(id,name,email,created_at,updated_at) values (?, 'History', ?, now(), now())", owner, owner + "@example.com");
        String family = mvc.perform(post("/api/families").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"History\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String familyId = com.jayway.jsonpath.JsonPath.read(family, "$.id");
        String member = mvc.perform(post("/api/families/" + familyId + "/members")
                .with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"Child\",\"memberType\":\"CHILD\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return "/api/families/" + familyId + "/children/" + com.jayway.jsonpath.JsonPath.read(member, "$.id");
    }

    @Test void deniesInaccessiblePdfBeforeReportingBusyCapacity() throws Exception {
        UUID owner = UUID.randomUUID(), outsider = UUID.randomUUID();
        String base = child(owner);
        jdbc.update("insert into beehome.users(id,name,email,created_at,updated_at) values (?, 'History', ?, now(), now())",
                outsider, outsider + "@example.com");
        var entered = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        var day = new com.beehome.history.dto.HistoryDetail(java.time.LocalDate.parse("2026-09-21"),
                UUID.randomUUID(), "Child", null, java.util.List.of(), java.util.List.of(), 0, 100, false,
                java.util.List.of(), java.util.List.of());
        try (var pool = Executors.newFixedThreadPool(2)) {
            var jobs = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 2; i++) {
                jobs.add(pool.submit(() -> pdfRenderer.render(owner, UUID.randomUUID(), () -> {
                    entered.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return day;
                })));
            }
            try {
                org.assertj.core.api.Assertions.assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                mvc.perform(get(base + "/history/2026-09-21/pdf")
                        .with(jwt().jwt(j -> j.subject(outsider.toString()))))
                        .andExpect(status().isNotFound());
                mvc.perform(get(base + "/history/2026-09-21/pdf")
                        .with(jwt().jwt(j -> j.subject(owner.toString()))))
                        .andExpect(status().isServiceUnavailable())
                        .andExpect(jsonPath("$.code").value("REPORT_BUSY"));
            } finally {
                release.countDown();
            }
            for (var job : jobs) job.get(10, TimeUnit.SECONDS);
        }
    }

    @Test void emptyDayAndMonthAreSuccessfulAndPrivate() throws Exception {
        UUID owner = UUID.randomUUID(), outsider = UUID.randomUUID();
        for (UUID user : new UUID[] {owner, outsider})
            jdbc.update("insert into beehome.users(id,name,email,created_at,updated_at) values (?, 'History', ?, now(), now())", user, user + "@example.com");
        String family = mvc.perform(post("/api/families").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"History\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String familyId = com.jayway.jsonpath.JsonPath.read(family, "$.id");
        String member = mvc.perform(post("/api/families/" + familyId + "/members")
                .with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"Child\",\"memberType\":\"CHILD\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String childId = com.jayway.jsonpath.JsonPath.read(member, "$.id");
        String base = "/api/families/" + familyId + "/children/" + childId;
        mvc.perform(get(base + "/history/2026-09-20").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.date").value("2026-09-20"))
                .andExpect(jsonPath("$.studies.length()").value(0));
        byte[] emptyPdf = mvc.perform(get(base + "/history/2026-09-20/pdf")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        try (var document = org.apache.pdfbox.Loader.loadPDF(emptyPdf)) {
            org.assertj.core.api.Assertions.assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(document))
                    .contains("Child", "2026-09-20").doesNotContain("Studies", "Photos");
        }
        mvc.perform(get(base + "/calendar?year=2026&month=9").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days.length()").value(0));
        mvc.perform(get(base + "/history/2026-09-20")).andExpect(status().isUnauthorized());
        mvc.perform(get(base + "/history/2026-09-20").with(jwt().jwt(j -> j.subject(outsider.toString()))))
                .andExpect(status().isNotFound());
    }

    @Test void combinesExistingSourcesAndReportsLiveTotals() throws Exception {
        UUID owner = UUID.randomUUID(); String base = child(owner);
        String family = base.substring(0, base.indexOf("/children"));
        String childId = base.substring(base.lastIndexOf('/') + 1);
        String member = family + "/members/" + childId;
        String bookJson = mvc.perform(post(family + "/books").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"title\":\"Together\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String bookId = com.jayway.jsonpath.JsonPath.read(bookJson, "$.id");
        String journeyJson = mvc.perform(post(base + "/books").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"bookId\":\"" + bookId + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String journeyId = com.jayway.jsonpath.JsonPath.read(journeyJson, "$.id");
        mvc.perform(post(base + "/reading-sessions").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"childBookId\":\"" + journeyId + "\",\"date\":\"2026-09-21\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post(member + "/daily-plan/2026-09-21/items").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"title\":\"Breakfast\",\"sortOrder\":0}"))
                .andExpect(status().isCreated());
        mvc.perform(put(member + "/executions/2026-09-21").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk());
        String subjectJson = mvc.perform(post(family + "/study-subjects").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"General\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String subjectId = com.jayway.jsonpath.JsonPath.read(subjectJson, "$.id");
        mvc.perform(post(member + "/study-sessions").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2026-09-21\",\"durationSeconds\":120,\"subjectId\":\"" + subjectId + "\"}"))
                .andExpect(status().isCreated());
        byte[] png = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        String upload = mvc.perform(multipart(family + "/media").file(new MockMultipartFile("file", "history.png", "image/png", png))
                .with(jwt().jwt(j -> j.subject(owner.toString())))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String mediaId = com.jayway.jsonpath.JsonPath.read(upload, "$.id");
        mvc.perform(post(base + "/photo-records").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"date\":\"2026-09-20\",\"mediaIds\":[\"" + mediaId + "\"]}"))
                .andExpect(status().isCreated());
        mvc.perform(get(base + "/history/2026-09-20").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.photos[0].media[0].id").value(mediaId))
                .andExpect(jsonPath("$.photos[0].date").value("2026-09-20"));
        byte[] pdf = mvc.perform(get(base + "/history/2026-09-20/pdf")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            boolean hasImage = false;
            for (var page : document.getPages())
                for (var name : page.getResources().getXObjectNames())
                    hasImage |= page.getResources().getXObject(name) instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
            org.assertj.core.api.Assertions.assertThat(hasImage).isTrue();
        }
        mvc.perform(get(base + "/calendar?year=2026&month=9").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].hasPhotos").value(true))
                .andExpect(jsonPath("$.days[1].hasRoutine").value(true))
                .andExpect(jsonPath("$.days[1].hasStudies").value(true))
                .andExpect(jsonPath("$.days[1].hasReading").value(true));
        mvc.perform(get(base + "/history?from=2026-09-20&to=2026-09-21&size=1")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].date").value("2026-09-21"))
                .andExpect(jsonPath("$.hasNext").value(true));
        mvc.perform(get(base + "/history?from=2026-09-20&to=2026-09-21&size=1&page=1")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].date").value("2026-09-20"))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get(base + "/reports?from=2026-09-20&to=2026-09-21")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.routine.days").value(1))
                .andExpect(jsonPath("$.studies.sessions").value(1))
                .andExpect(jsonPath("$.studies.totalMinutes").value(2.0))
                .andExpect(jsonPath("$.photos.records").value(1))
                .andExpect(jsonPath("$.reading.sessions").value(1));
        mvc.perform(get(base + "/reports?from=2026-09-21&to=2026-09-20")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get(base + "/reports?from=2025-01-01&to=2026-09-21")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get(base + "/reports?to=2026-09-21")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test void usesFamilyDateForTimerAndRejectsAdultOrForeignChild() throws Exception {
        UUID owner = UUID.randomUUID(), other = UUID.randomUUID();
        String base = child(owner), foreignBase = child(other);
        String family = base.substring(0, base.indexOf("/children"));
        String childId = base.substring(base.lastIndexOf('/') + 1);
        String member = family + "/members/" + childId;
        String subjectJson = mvc.perform(post(family + "/study-subjects").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"General\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String subjectId = com.jayway.jsonpath.JsonPath.read(subjectJson, "$.id");
        org.mockito.Mockito.when(clock.instant()).thenReturn(java.time.Instant.parse("2026-09-21T02:59:59Z"));
        mvc.perform(post(member + "/study-sessions/start").header("X-Timezone-Offset", "-180").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"subjectId\":\"" + subjectId + "\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.date").value("2026-09-20"));
        mvc.perform(get(base + "/calendar?year=2026&month=9").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days[0].date").value("2026-09-20"));
        String foreignChild = foreignBase.substring(foreignBase.lastIndexOf('/') + 1);
        mvc.perform(get(family + "/children/" + foreignChild + "/history/2026-09-20")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isNotFound());
        String adult = mvc.perform(post(family + "/members").with(jwt().jwt(j -> j.subject(owner.toString())))
                .contentType("application/json").content("{\"name\":\"Adult\",\"memberType\":\"ADULT\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String adultId = com.jayway.jsonpath.JsonPath.read(adult, "$.id");
        mvc.perform(get(family + "/children/" + adultId + "/reports?from=2026-09-20&to=2026-09-20")
                .with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isNotFound());
        mvc.perform(get(base + "/calendar?year=2026&month=13").with(jwt().jwt(j -> j.subject(owner.toString()))))
                .andExpect(status().isBadRequest());
    }

    @Test void reportQueryCountDoesNotGrowWithPeriodLength() throws Exception {
        UUID owner = UUID.randomUUID(); String base = child(owner);
        var statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        mvc.perform(get(base + "/reports?from=2026-09-21&to=2026-09-21")
                .with(jwt().jwt(j -> j.subject(owner.toString())))).andExpect(status().isOk());
        long oneDay = statistics.getPrepareStatementCount();
        statistics.clear();
        mvc.perform(get(base + "/reports?from=2025-09-21&to=2026-09-21")
                .with(jwt().jwt(j -> j.subject(owner.toString())))).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(oneDay + 1);
    }
}
