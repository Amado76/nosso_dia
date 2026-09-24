package com.beehome.media.controller;

import com.beehome.shared.exception.GlobalExceptionHandler;
import com.beehome.shared.localization.LocalizationService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.multipart.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MediaMultipartLimitTests {
    @Test void returnsStableErrorWhenParsingFailsBeforeControllerSelection() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class); context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).build();
            mvc.perform(post("/api/families/00000000-0000-0000-0000-000000000001/media")
                            .contentType("multipart/form-data; boundary=test"))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("MEDIA_TOO_LARGE"));
        }
    }

    @TestConfiguration @EnableWebMvc
    static class Config {
        @Bean MediaController controller() { return new MediaController(null); }
        @Bean GlobalExceptionHandler errors() {
            var messages = new ResourceBundleMessageSource(); messages.setBasename("messages");
            return new GlobalExceptionHandler(new LocalizationService(messages));
        }
        @Bean MediaUploadExceptionHandler uploadErrors() { return new MediaUploadExceptionHandler(errors()); }
        @Bean MultipartResolver multipartResolver() {
            // MockMvc's multipart builder bypasses parsing; model the resolver's actual failure instead.
            return new MultipartResolver() {
                public boolean isMultipart(HttpServletRequest request) { return true; }
                public MultipartHttpServletRequest resolveMultipart(HttpServletRequest request) {
                    throw new MaxUploadSizeExceededException(11534336);
                }
                public void cleanupMultipart(MultipartHttpServletRequest request) {}
            };
        }
    }
}
