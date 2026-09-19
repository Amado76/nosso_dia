package com.nossodia.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    @Bean
    OpenAPI nossoDiaOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Nosso Dia API")
                .description("Nosso Dia backend")
                .version("v1"));
    }
}
