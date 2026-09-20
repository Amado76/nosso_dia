package com.nossodia;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "spring.security.user.name=test",
        "spring.security.user.password=test-password"
})
@AutoConfigureMockMvc
class SwaggerUiTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void servesSwaggerUiWithValidCredentials() throws Exception {
        mvc.perform(get("/swagger-ui/index.html").with(httpBasic("test", "test-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-ui.css").with(httpBasic("test", "test-password")))
                .andExpect(status().isOk());
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js").with(httpBasic("test", "test-password")))
                .andExpect(status().isOk());
    }

    @Test
    void redirectsSwaggerShortcutWithValidCredentials() throws Exception {
        mvc.perform(get("/swagger").with(httpBasic("test", "test-password")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }

    @Test
    void redirectsSwaggerUiDirectoryWithValidCredentials() throws Exception {
        mvc.perform(get("/swagger-ui/").with(httpBasic("test", "test-password")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }

    @Test
    void requiresAuthenticationForSwaggerShortcut() throws Exception {
        mvc.perform(get("/swagger"))
                .andExpect(status().isUnauthorized());
    }
}
