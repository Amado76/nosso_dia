package com.nossodia.config;

import com.nossodia.auth.security.AuthProperties;
import com.nossodia.auth.security.AuthRateLimitFilter;
import com.nossodia.auth.security.SecurityErrorHandler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthProperties properties,
            Clock clock, SecurityErrorHandler errors) throws Exception {
        return http
                // Bearer headers and JSON credentials are explicit; cookies and HTTP Basic are not accepted.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(requests -> {
                    requests.requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                            "/api/auth/refresh", "/api/auth/forgot-password", "/api/auth/reset-password").permitAll();
                    requests.requestMatchers(HttpMethod.GET, "/api/health").permitAll();
                    if (properties.publicDocs()) requests.requestMatchers(HttpMethod.GET,
                            "/swagger", "/swagger/", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll();
                    requests.anyRequest().authenticated();
                })
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> {}).authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .addFilterBefore(new AuthRateLimitFilter(properties, clock, errors), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
