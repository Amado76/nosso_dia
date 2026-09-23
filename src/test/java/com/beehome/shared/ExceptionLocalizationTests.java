package com.beehome.shared;

import com.beehome.shared.exception.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ExceptionLocalizationTests.FixtureController.class,
        properties = "spring.messages.basename=messages,test_messages")
@Import({ExceptionLocalizationTests.FixtureController.class,
        com.beehome.shared.localization.LocalizationService.class,
        com.beehome.shared.localization.LocalizationConfiguration.class})
class ExceptionLocalizationTests {
    @Autowired
    MockMvc mvc;

    @Test
    void returnsSafeLocalizedUnexpectedError() throws Exception {
        mvc.perform(get("/api/test-errors/unexpected").with(user("test"))
                        .header("Accept-Language", "pt-BR"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado"));
    }

    @ParameterizedTest
    @CsvSource({
            "en,An unexpected error occurred",
            "pt,Ocorreu um erro inesperado",
            "pt-BR,Ocorreu um erro inesperado",
            "pt-PT,Ocorreu um erro inesperado",
            "es,Ocurrió un error inesperado",
            "es-PY,Ocurrió un error inesperado",
            "es-CO,Ocurrió un error inesperado",
            "en-GB,An unexpected error occurred",
            "fr,An unexpected error occurred",
            ",An unexpected error occurred",
            "'fr;q=1.0, es-PY;q=0.8, en;q=0.5',Ocurrió un error inesperado"
    })
    void resolvesRequestLanguage(String language, String detail) throws Exception {
        var request = get("/api/test-errors/unexpected").with(user("test"));
        if (language != null) {
            request.header("Accept-Language", language);
        }
        mvc.perform(request)
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.instance").value("/api/test-errors/unexpected"))
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.detail").value(detail))
                .andExpect(content().string(not(containsString("Internal database information"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({
            "en,Item \"sample\" was not found",
            "pt-BR,O item \"sample\" não foi encontrado",
            "es-PY,No se encontró el elemento \"sample\""
    })
    void handlesApplicationErrorsWithArguments(String language, String detail) throws Exception {
        mvc.perform(get("/api/test-errors/expected").with(user("test")).header("Accept-Language", language))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.code").value("TEST_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value(detail));
    }

    @ParameterizedTest
    @CsvSource({
            "en,Validation failed,This field is required",
            "pt-BR,Falha na validação,Este campo é obrigatório",
            "es-PY,Error de validación,Este campo es obligatorio"
    })
    void localizesValidationMessages(String language, String title, String message) throws Exception {
        mvc.perform(post("/api/test-errors/validation").with(user("test")).with(csrf())
                        .header("Accept-Language", language)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value(message))
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
    }

    @ParameterizedTest
    @CsvSource({
            "en,This field is required",
            "pt-BR,Este campo é obrigatório",
            "es-PY,Este campo es obligatorio"
    })
    void localizesMethodParameterValidation(String language, String message) throws Exception {
        mvc.perform(get("/api/test-errors/parameter").with(user("test")).param("name", "")
                        .header("Accept-Language", language))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value(message));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void logsUnexpectedStackTraceWithoutExceptionMessage(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/test-errors/unexpected").with(user("test")))
                .andExpect(status().isInternalServerError());
        assertThat(output.getOut())
                .contains("Unexpected error while processing request", "FixtureController.unexpected")
                .doesNotContain("Internal database information");
    }

    @Test
    void preservesAccessDeniedStatus() throws Exception {
        mvc.perform(get("/api/test-errors/forbidden").with(user("test")))
                .andExpect(status().isForbidden());
    }

    @Test
    void preservesFrameworkStatuses() throws Exception {
        mvc.perform(post("/api/test-errors/unexpected").with(user("test")).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/api/test-errors/validation").with(user("test")).with(csrf())
                        .contentType(MediaType.TEXT_PLAIN).content("invalid"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/test-errors/validation").with(user("test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/test-errors/absent").with(user("test")))
                .andExpect(status().isNotFound());
    }

    static class MissingItemException extends ApiException {
        MissingItemException() {
            super(HttpStatus.NOT_FOUND, "TEST_NOT_FOUND", "test.error.missing", "sample");
        }
    }

    record Input(@NotBlank(message = "{validation.required}") String name) {}

    @RestController
    static class FixtureController {
        @GetMapping("/api/test-errors/parameter")
        void parameter(@RequestParam(name = "name") @NotBlank(message = "{validation.required}") String name) {}

        @GetMapping("/api/test-errors/forbidden")
        void forbidden() {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }

        @GetMapping("/api/test-errors/expected")
        void expected() {
            throw new MissingItemException();
        }

        @PostMapping("/api/test-errors/validation")
        void validate(@Valid @RequestBody Input input) {}

        @GetMapping("/api/test-errors/unexpected")
        void unexpected() {
            throw new IllegalStateException("Internal database information");
        }
    }
}
