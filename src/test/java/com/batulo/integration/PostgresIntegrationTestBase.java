package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base des tests d'intégration de la phase 2 : un vrai PostgreSQL 16 (Testcontainers), pour tester le JSONB et
 * les contraintes telles qu'en production. Le conteneur est démarré une seule fois et partagé par toutes les
 * classes de test ; Testcontainers le supprime à la fin. Le mapping JPA est validé contre le schéma Liquibase.
 */
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
abstract class PostgresIntegrationTestBase extends IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    /** Gestionnaire autorisé (ou non) à créer des sondages. */
    Utilisateur gestionnaire(String courriel, Utilisateur admin, boolean peutCreerSondages) {
        Utilisateur gestionnaire = gestionnaire(courriel, admin);
        gestionnaire.setPeutCreerSondages(peutCreerSondages);
        return utilisateurRepository.save(gestionnaire);
    }
}
