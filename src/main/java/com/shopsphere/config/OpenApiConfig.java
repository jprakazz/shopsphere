package com.shopsphere.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Swagger UI at /swagger-ui.html, with an "Authorize" button that accepts the JWT from /api/auth/login. */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI shopsphereOpenApi() {
        return new OpenAPI()
                .info(new Info().title("ShopSphere API").version("1.0")
                        .description("E-commerce backend: catalogue, cart, checkout, payments and order tracking"))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
