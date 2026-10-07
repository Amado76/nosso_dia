package com.beehome;

import java.util.UUID;
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
class Pdr09ActivityTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void activityCatalogAndRecordsAppearInDailyAndPeriodViewsWithoutChangingStudyTotals() throws Exception {
        UUID owner = UUID.randomUUID();
        jdbc.update("insert into beehome.users(id,name,email,created_at,updated_at) values (?, 'Owner', ?, now(), now())", owner, owner + "@example.com");
        var auth = jwt().jwt(j -> j.subject(owner.toString()));
        String family = mvc.perform(post("/api/families").with(auth).contentType("application/json")
                .content("{\"name\":\"Family\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String familyId = com.jayway.jsonpath.JsonPath.read(family, "$.id");
        String base = "/api/families/" + familyId;
        String child = mvc.perform(post(base + "/members").with(auth).contentType("application/json")
                .content("{\"name\":\"Elias\",\"memberType\":\"CHILD\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String childId = com.jayway.jsonpath.JsonPath.read(child, "$.id");
        String subject = mvc.perform(post(base + "/study-subjects").with(auth)
                .contentType("application/json").content("{\"name\":\"Science\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String subjectId = com.jayway.jsonpath.JsonPath.read(subject, "$.id");
        String tag = mvc.perform(post(base + "/tags").with(auth)
                .contentType("application/json").content("{\"name\":\"Science\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String tagId = com.jayway.jsonpath.JsonPath.read(tag, "$.id");
        String secondTag = mvc.perform(post(base + "/tags").with(auth)
                .contentType("application/json").content("{\"name\":\"Outdoors\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String secondTagId = com.jayway.jsonpath.JsonPath.read(secondTag, "$.id");
        mvc.perform(post(base + "/members/" + childId + "/study-sessions").with(auth)
                .contentType("application/json").content("{\"subjectId\":\"" + subjectId + "\",\"date\":\"2026-09-21\",\"durationMinutes\":60,\"topic\":\"Cells\",\"description\":\"Observed samples\",\"comments\":\"Independent work\",\"material\":\"Microscope\",\"startPage\":2,\"endPage\":4}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.topic").value("Cells"))
                .andExpect(jsonPath("$.durationMinutes").value(60));
        String activity = mvc.perform(post(base + "/extracurricular-activities").with(auth)
                .contentType("application/json").content("{\"name\":\"Museum Visit\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String activityId = com.jayway.jsonpath.JsonPath.read(activity, "$.id");
        String record = mvc.perform(post(base + "/children/" + childId + "/extracurricular-records").with(auth)
                .contentType("application/json").content("{\"activityId\":\"" + activityId + "\",\"date\":\"2026-09-21\",\"topic\":\"Dinosaurs\",\"durationMinutes\":180,\"description\":\"Saw fossils\",\"comments\":\"Asked questions\",\"material\":\"Museum guide\",\"startPage\":2,\"endPage\":4,\"tagIds\":[\"" + tagId + "\",\"" + secondTagId + "\"]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String recordId = com.jayway.jsonpath.JsonPath.read(record, "$.id");
        mvc.perform(get(base + "/children/" + childId + "/history/2026-09-21").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.extracurricularActivities[0].id").value(recordId))
                .andExpect(jsonPath("$.extracurricularActivities[0].activity.name").value("Museum Visit"))
                .andExpect(jsonPath("$.extracurricularActivities[0].description").value("Saw fossils"))
                .andExpect(jsonPath("$.extracurricularActivities[0].comments").value("Asked questions"))
                .andExpect(jsonPath("$.extracurricularActivities[0].material").value("Museum guide"))
                .andExpect(jsonPath("$.extracurricularActivities[0].startPage").value(2))
                .andExpect(jsonPath("$.extracurricularActivities[0].endPage").value(4))
                .andExpect(jsonPath("$.extracurricularActivities[0].tags.length()").value(2));
        mvc.perform(get(base + "/children/" + childId + "/reports?from=2026-09-21&to=2026-09-21").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studies.sessions").value(1))
                .andExpect(jsonPath("$.studies.subjects[0].subjectId").value(subjectId))
                .andExpect(jsonPath("$.studies.subjects[0].records").value(1))
                .andExpect(jsonPath("$.studies.subjects[0].minutes").value(60))
                .andExpect(jsonPath("$.extracurricularActivities.activities[0].activityId").value(activityId))
                .andExpect(jsonPath("$.extracurricularActivities.activities[0].durationMinutes").value(180));
        byte[] pdf = mvc.perform(get(base + "/children/" + childId + "/history/2026-09-21/pdf").with(auth))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"daily-report-elias-2026-09-21.pdf\""))
                .andReturn().getResponse().getContentAsByteArray();
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            org.assertj.core.api.Assertions.assertThat(text).contains("Elias", "Science", "Cells", "Museum Visit", "Dinosaurs");
        }
        mvc.perform(get(base + "/children/" + childId + "/calendar?year=2026&month=9").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.days[0].hasExtracurricularActivities").value(true));
        mvc.perform(get(base + "/children/" + childId + "/history/2026-09-22").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.extracurricularActivities.length()").value(0));
        mvc.perform(post(base + "/children/" + childId + "/extracurricular-records").with(auth)
                .contentType("application/json").content("{\"activityId\":\"" + activityId + "\",\"date\":\"2026-09-21\",\"durationMinutes\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post(base + "/members/" + childId + "/study-sessions").with(auth)
                .contentType("application/json").content("{\"date\":\"2026-09-21\",\"durationMinutes\":30,\"topic\":\"Cells\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post(base + "/members/" + childId + "/study-sessions").with(auth)
                .contentType("application/json").content("{\"date\":\"2026-09-21\",\"durationSeconds\":120}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post(base + "/children/" + childId + "/extracurricular-records").with(auth)
                .contentType("application/json").content("{\"activityId\":\"" + activityId + "\",\"date\":\"2026-09-21\",\"startPage\":5,\"endPage\":4}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post(base + "/members/" + childId + "/study-sessions").with(auth)
                .contentType("application/json").content("{\"subjectId\":\"" + subjectId + "\",\"date\":\"2026-09-21\",\"durationMinutes\":5,\"startPage\":8,\"endPage\":7}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get(base + "/children/" + childId + "/history/2026-09-21/pdf"))
                .andExpect(status().isUnauthorized());
        UUID outsider = UUID.randomUUID();
        jdbc.update("insert into beehome.users(id,name,email,created_at,updated_at) values (?, 'Outsider', ?, now(), now())", outsider, outsider + "@example.com");
        mvc.perform(get(base + "/children/" + childId + "/history/2026-09-21/pdf")
                .with(jwt().jwt(j -> j.subject(outsider.toString())))).andExpect(status().isNotFound());
        String otherFamily = mvc.perform(post("/api/families").with(auth).contentType("application/json")
                .content("{\"name\":\"Other\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String otherBase = "/api/families/" + com.jayway.jsonpath.JsonPath.<String>read(otherFamily, "$.id");
        String foreignTag = mvc.perform(post(otherBase + "/tags").with(auth).contentType("application/json")
                .content("{\"name\":\"Foreign\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String foreignTagId = com.jayway.jsonpath.JsonPath.read(foreignTag, "$.id");
        mvc.perform(post(base + "/children/" + childId + "/extracurricular-records").with(auth)
                .contentType("application/json").content("{\"activityId\":\"" + activityId + "\",\"date\":\"2026-09-21\",\"tagIds\":[\"" + foreignTagId + "\"]}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("TAG_NOT_FOUND"));
        mvc.perform(patch(base + "/extracurricular-activities/" + activityId).with(auth)
                .contentType("application/json").content("{\"name\":\"Natural History Museum\"}"))
                .andExpect(status().isOk());
        mvc.perform(post(base + "/extracurricular-activities").with(auth)
                .contentType("application/json").content("{\"name\":\"natural history museum\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ACTIVITY_DUPLICATE"));
        mvc.perform(get(base + "/children/" + childId + "/history/2026-09-21").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.extracurricularActivities[0].activity.name").value("Natural History Museum"));
        mvc.perform(patch(base + "/study-subjects/" + subjectId).with(auth)
                .contentType("application/json").content("{\"name\":\"Natural Science\"}"))
                .andExpect(status().isOk());
        mvc.perform(get(base + "/children/" + childId + "/history/2026-09-21").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.studies[0].subjectName").value("Natural Science"));
        String minutesOnly = mvc.perform(post(base + "/members/" + childId + "/study-sessions").with(auth)
                .contentType("application/json").content("{\"subjectId\":\"" + subjectId + "\",\"date\":\"2026-09-22\",\"durationMinutes\":30}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String minutesOnlyId = com.jayway.jsonpath.JsonPath.read(minutesOnly, "$.id");
        mvc.perform(patch(base + "/members/" + childId + "/study-sessions/" + minutesOnlyId).with(auth)
                .contentType("application/json").content("{\"subjectId\":null}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post(base + "/extracurricular-activities/" + activityId + "/deactivate").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(post(base + "/children/" + childId + "/extracurricular-records").with(auth)
                .contentType("application/json").content("{\"activityId\":\"" + activityId + "\",\"date\":\"2026-09-22\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ACTIVITY_NOT_FOUND"));
        mvc.perform(get(base + "/children/" + childId + "/history/2026-09-21").with(auth))
                .andExpect(status().isOk()).andExpect(jsonPath("$.extracurricularActivities[0].activity.id").value(activityId));
    }

    @Test void pdfPaginatesLongDailyContent() throws Exception {
        var date = java.time.LocalDate.parse("2026-09-21");
        var entries = new java.util.ArrayList<com.beehome.activity.dto.ActivityRecordResponse>();
        for (int i = 0; i < 100; i++)
            entries.add(new com.beehome.activity.dto.ActivityRecordResponse(UUID.randomUUID(), UUID.randomUUID(), date,
                    new com.beehome.activity.dto.ActivityRecordResponse.Activity(UUID.randomUUID(), "Museum Visit"),
                    "Exhibit " + i, 30, "Observed fossils and discussed the animals that lived here.",
                    null, null, null, null, java.util.List.of(), UUID.randomUUID(), java.time.Instant.EPOCH,
                    java.time.Instant.EPOCH, 0));
        var detail = new com.beehome.history.dto.HistoryDetail(date, UUID.randomUUID(), "José", null,
                java.util.List.of(), java.util.List.of(), 0, 100, false, java.util.List.of(), entries);
        byte[] pdf = new com.beehome.history.pdf.DailyReportPdfRenderer(org.mockito.Mockito.mock(com.beehome.media.service.MediaService.class), 8_388_608, 2)
                .render(UUID.randomUUID(), UUID.randomUUID(), detail);
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            org.assertj.core.api.Assertions.assertThat(document.getNumberOfPages()).isGreaterThan(1);
            org.assertj.core.api.Assertions.assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(document))
                    .contains("José", "Exhibit 0", "Exhibit 99");
        }
    }

    @Test void pdfEmbedsResizedPhotoWithoutChangingStoredMedia() throws Exception {
        var image = new java.awt.image.BufferedImage(2000, 1000, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", bytes);
        UUID user = UUID.randomUUID(), family = UUID.randomUUID(), mediaId = UUID.randomUUID();
        var media = org.mockito.Mockito.mock(com.beehome.media.service.MediaService.class);
        org.mockito.Mockito.when(media.content(user, family, mediaId)).thenReturn(
                new com.beehome.media.service.MediaService.Content(
                        new org.springframework.core.io.ByteArrayResource(bytes.toByteArray()), "image/png"));
        var date = java.time.LocalDate.parse("2026-09-21");
        var detail = new com.beehome.history.dto.HistoryDetail(date, UUID.randomUUID(), "Elias", null,
                java.util.List.of(), java.util.List.of(), 0, 100, false,
                java.util.List.of(new com.beehome.history.dto.HistoryDetail.Photo(UUID.randomUUID(), date, null,
                        java.util.List.of(), java.util.List.of(new com.beehome.history.dto.HistoryDetail.Photo.Media(mediaId, 0)))),
                java.util.List.of());
        byte[] pdf = new com.beehome.history.pdf.DailyReportPdfRenderer(media, 8_388_608, 2).render(user, family, detail);
        try (var document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            var page = document.getPage(0);
            var names = page.getResources().getXObjectNames().iterator();
            org.assertj.core.api.Assertions.assertThat(names.hasNext()).isTrue();
            var embedded = (org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject) page.getResources().getXObject(names.next());
            org.assertj.core.api.Assertions.assertThat(embedded.getWidth()).isLessThanOrEqualTo(1010);
            org.assertj.core.api.Assertions.assertThat(embedded.getHeight()).isLessThanOrEqualTo(380);
        }
        org.assertj.core.api.Assertions.assertThat(image.getWidth()).isEqualTo(2000);
    }
}
