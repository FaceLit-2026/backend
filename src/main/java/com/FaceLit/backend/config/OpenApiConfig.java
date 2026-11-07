package com.FaceLit.backend.config;

import java.util.List;
import java.util.Set;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Describes existing HTTP contracts; it does not rename or implement routes. */
@Configuration
public class OpenApiConfig {
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/auth/login", "/api/auth/request-recovery", "/api/auth/verify-token",
            "/api/auth/reset-password", "/api/auth/complete-access", "/api/auth/privacy-acceptance-status");

    @Bean
    public OpenAPI faceLitOpenApi() {
        return new OpenAPI()
                .info(new Info().title("FaceLit Backend API").version("1.0.0")
                        .description("Current FaceLit HTTP contracts. Existing /api paths are preserved; roles and ownership are enforced by the server."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public OpenApiCustomizer publicAuthenticationOperations() {
        return api -> {
            if (api.getPaths() == null) { return; }
            api.getPaths().forEach((path, item) -> {
                if (PUBLIC_AUTH_PATHS.contains(path)) {
                    item.readOperations().forEach(operation -> operation.setSecurity(List.of()));
                }
            });
        };
    }
}
