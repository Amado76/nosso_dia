package com.beehome;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "auth.allow-ephemeral-key=true")
@AutoConfigureMockMvc
class BeeHomeApplicationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test void servesPublicHealth() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
    @Test void appliesMigrationsToPostgres() {
        assertEquals(16, jdbc.queryForObject("SELECT count(*) FROM public.flyway_schema_history WHERE success", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'beehome'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'beehome' AND table_name IN ('daily_executions', 'daily_execution_items')", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'beehome' AND table_name IN ('study_subjects', 'study_sessions')", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'beehome' AND table_name IN ('media', 'photo_records', 'photo_record_media')", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'beehome' AND table_name IN ('books', 'child_books', 'reading_sessions')", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'beehome' AND table_name = 'drawings'", Integer.class));
        assertEquals(5, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema = 'beehome' AND table_name IN ('tags', 'book_tags', 'study_session_tags', 'routine_item_tags', 'daily_plan_item_tags')", Integer.class));
    }
    @Test void protectsDocumentationByDefault() throws Exception {
        for (String path : new String[]{"/v3/api-docs", "/v3/api-docs/swagger-config", "/swagger-ui/index.html",
                "/swagger-ui/swagger-ui.css", "/swagger-ui/swagger-ui-bundle.js", "/swagger-ui.html", "/swagger"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
    @Test void rejectsLegacyBasicAuthentication() throws Exception {
        mvc.perform(get("/api/users/me").with(
                org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("dev", "test-password")))
                .andExpect(status().isUnauthorized());
    }
}
