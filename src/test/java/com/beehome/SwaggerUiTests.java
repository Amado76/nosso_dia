package com.beehome;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {"auth.allow-ephemeral-key=true", "auth.public-docs=true"})
@AutoConfigureMockMvc
class SwaggerUiTests {
    @Autowired MockMvc mvc;

    @Test void servesDevelopmentSwaggerUi() throws Exception {
        for (String path : new String[]{"/swagger-ui/index.html", "/swagger-ui/swagger-ui.css", "/swagger-ui/swagger-ui-bundle.js"})
            mvc.perform(get(path)).andExpect(status().isOk());
    }
    @Test void redirectsSwaggerShortcuts() throws Exception {
        for (String path : new String[]{"/swagger", "/swagger-ui/"})
            mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/swagger-ui/index.html"));
    }
    @Test void documentsBearerAuthenticationAndPublicLogin() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("BeeHome API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/users/me'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist());
    }

    @Test void documentsReadingResourcesAndMetrics() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/families/{familyId}/books'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/families/{familyId}/children/{childId}/books/{childBookId}'].patch").exists())
                .andExpect(jsonPath("$.paths['/api/families/{familyId}/children/{childId}/reading-sessions/{sessionId}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/families/{familyId}/children/{childId}/reading-summary'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.components.schemas.ReadingSummary.properties.booksCompleted").exists());
    }
    @Test void documentsTypedHistoryReadingAndReportTotals() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.HistoryDetail.properties.reading.items['$ref']")
                        .value("#/components/schemas/HistoryReadingSession"))
                .andExpect(jsonPath("$.components.schemas.HistoryReadingSession.properties.bookId").exists())
                .andExpect(jsonPath("$.components.schemas.HistoryCalendar.properties.days.items['$ref']")
                        .value("#/components/schemas/HistoryCalendarDay"))
                .andExpect(jsonPath("$.components.schemas.HistoryPage.properties.items.items['$ref']")
                        .value("#/components/schemas/HistoryPeriodDay"))
                .andExpect(jsonPath("$.components.schemas.HistoryPeriodDay.properties.readingSessions").exists())
                .andExpect(jsonPath("$.components.schemas.HistoryReadingSession.properties.bookTitle").exists())
                .andExpect(jsonPath("$.components.schemas.HistoryDetail.properties.readingHasNext").exists())
                .andExpect(jsonPath("$.components.schemas.HistoryReadingTotals.properties.books").exists())
                .andExpect(jsonPath("$.components.schemas.HistoryReadingTotals.properties.totalMinutes").exists())
                .andExpect(jsonPath("$.components.schemas.HistoryReadingTotals.properties.booksCompleted").exists())
                .andExpect(jsonPath("$.paths['/api/families/{familyId}/children/{childId}/history/{date}'].get.parameters[?(@.name == 'readingSize')].schema.maximum")
                        .value(org.hamcrest.Matchers.contains(100)));
    }

}
