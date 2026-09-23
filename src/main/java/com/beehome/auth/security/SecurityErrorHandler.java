package com.beehome.auth.security;

import com.beehome.shared.localization.LocalizationService;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.net.URI;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.*;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final LocalizationService localization;
    private final LocaleResolver locales;
    private final ObjectMapper mapper;
    public SecurityErrorHandler(LocalizationService localization, LocaleResolver locales, ObjectMapper mapper) {
        this.localization = localization; this.locales = locales; this.mapper = mapper;
    }
    @Override public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "error.auth.unauthenticated");
    }
    @Override public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception) throws IOException {
        write(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "error.auth.forbidden");
    }
    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String key) throws IOException {
        var previous = LocaleContextHolder.getLocaleContext();
        try {
            LocaleContextHolder.setLocale(locales.resolveLocale(request));
            var problem = ProblemDetail.forStatusAndDetail(status, localization.get(key));
            problem.setInstance(URI.create(request.getRequestURI()));
            problem.setProperty("code", code);
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(), problem);
        } finally {
            LocaleContextHolder.setLocaleContext(previous);
        }
    }
}
