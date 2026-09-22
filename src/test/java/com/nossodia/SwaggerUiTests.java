package com.nossodia;

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
                .andExpect(jsonPath("$.info.title").value("Nosso Dia API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/users/me'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/auth/logout'].post.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").doesNotExist());
    }
}
