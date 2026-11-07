package com.batulo.configs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentation Swagger, disponible sur /swagger-ui.html.
 * Le schéma bearerAuth (bouton « Authorize » pour le jeton JWT) sera ajouté à l'étape 7, avec la sécurité.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Batulo API")
                        .description("Plateforme SaaS de sondages : comptes et abonnements (phase 1)")
                        .version("0.1.0"));
    }
}
