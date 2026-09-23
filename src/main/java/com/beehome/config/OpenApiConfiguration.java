package com.beehome.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI beeHomeOpenApi() {
        return new OpenAPI().components(new io.swagger.v3.oas.models.Components()
                .addSecuritySchemes("bearerAuth", new io.swagger.v3.oas.models.security.SecurityScheme()
                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                        .scheme("bearer").bearerFormat("JWT"))).info(new Info()
                .title("BeeHome API")
                .description("BeeHome backend")
                .version("v1"));
    }
}
