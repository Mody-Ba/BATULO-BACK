package com.batulo.configs;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation Swagger, disponible sur /swagger-ui.html. Bouton « Authorize » : coller le jeton renvoyé par
 * POST /api/auth/connexion (sans le préfixe « Bearer »). Les routes publiques sont marquées @SecurityRequirements.
 */
@Configuration
public class OpenApiConfig {

    public static final String SCHEMA_JWT = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Batulo API")
                        .description("Plateforme SaaS de sondages : comptes et abonnements (phase 1). "
                                + "Connectez-vous avec POST /api/auth/connexion puis collez le jeton dans « Authorize ».")
                        .version("1.0.0"))
                .components(new Components().addSecuritySchemes(SCHEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Jeton d'accès renvoyé par /api/auth/connexion (valable 15 minutes)")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEMA_JWT));
    }
}
