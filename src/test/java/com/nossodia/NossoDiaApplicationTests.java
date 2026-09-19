package com.nossodia;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.security.user.name=test",
        "spring.security.user.password=test-password"
})
@AutoConfigureMockMvc
class NossoDiaApplicationTests {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    @Test
    void appliesInitialMigrationToPostgres() {
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM public.flyway_schema_history WHERE version = '1' AND success",
                Integer.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'nosso_dia'",
                Integer.class));
    }

    @Test
    void requiresAuthenticationForApiDocumentation() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void servesApiDocumentationWithValidCredentials() throws Exception {
        mvc.perform(get("/v3/api-docs").with(httpBasic("test", "test-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Nosso Dia API"));
    }

    @Test
    void rejectsInvalidCredentials() throws Exception {
        mvc.perform(get("/v3/api-docs").with(httpBasic("test", "wrong-password")))
                .andExpect(status().isUnauthorized());
    }

}
