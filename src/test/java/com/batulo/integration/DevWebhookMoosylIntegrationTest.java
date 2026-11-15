package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.batulo.service.model.SessionMoosylModel;
import com.batulo.service.paiement.MoosylClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Route de développement qui simule Moosyl : le webhook signé part en HTTP vers le serveur démarré sur un port
 * aléatoire, comme en local sans tunnel.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("dev")
class DevWebhookMoosylIntegrationTest extends PostgresIntegrationTestBase {

    @MockitoBean
    MoosylClient moosylClient;

    private Utilisateur admin;
    private String jetonAdmin;

    @BeforeEach
    void preparer() throws Exception {
        superAdmin("sa@test.com");
        admin = admin("awa@test.com", null);
        jetonAdmin = jeton("awa@test.com");
        when(moosylClient.creerSession(anyString(), anyLong(), any(), anyString(), anyInt()))
                .thenAnswer(appel -> new SessionMoosylModel("cs_dev", "https://payments.moosyl.com/checkout/cs_dev",
                        Instant.now()));
        when(moosylClient.statutSession(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void simulerUnWebhookSigneActiveLAbonnementUneFois() throws Exception {
        String reference = lire(postAvec(jetonAdmin, "/api/abonnements/paiement-mobile", Map.of("formule", "MENSUEL"))
                .andExpect(status().isCreated()));

        // Montant différent : refusé par le vrai webhook, paiement toujours en attente.
        postAvec(jetonAdmin, "/api/dev/paiements/moosyl/webhook", Map.of("reference", reference, "montant", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statutHttp").value(200))
                .andExpect(jsonPath("$.statutPaiement").value("EN_ATTENTE"));

        postAvec(jetonAdmin, "/api/dev/paiements/moosyl/webhook", Map.of("reference", reference))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.evenement").value("payment-updated"))
                .andExpect(jsonPath("$.statutEnvoye").value("completed"))
                .andExpect(jsonPath("$.statutHttp").value(200))
                .andExpect(jsonPath("$.statutPaiement").value("REUSSI"));
        getAvec(jetonAdmin, "/api/abonnements/moi").andExpect(jsonPath("$.statut").value("ACTIF"));
        Object expiration = jdbcTemplate.queryForObject("select date_expiration from abonnement where entreprise_id = ?",
                Object.class, admin.getEntreprise().getId());

        // Simulé une seconde fois : idempotent.
        postAvec(jetonAdmin, "/api/dev/paiements/moosyl/webhook", Map.of("reference", reference))
                .andExpect(jsonPath("$.statutPaiement").value("REUSSI"));
        assertThat(jdbcTemplate.queryForObject("select date_expiration from abonnement where entreprise_id = ?",
                Object.class, admin.getEntreprise().getId())).isEqualTo(expiration);
    }

    @Test
    void simulerUnEchecEtLesErreurs() throws Exception {
        String reference = lire(postAvec(jetonAdmin, "/api/abonnements/paiement-mobile", Map.of("formule", "ANNUEL"))
                .andExpect(status().isCreated()));
        postAvec(jetonAdmin, "/api/dev/paiements/moosyl/webhook", Map.of("reference", reference, "statut", "failed"))
                .andExpect(jsonPath("$.statutPaiement").value("ECHOUE"));
        postAvec(jetonAdmin, "/api/dev/paiements/moosyl/webhook", Map.of("reference", reference, "statut", "paye"))
                .andExpect(status().isBadRequest());

        // Paiement d'une autre entreprise : 404.
        adminActif("autre@test.com");
        postAvec(jeton("autre@test.com"), "/api/dev/paiements/moosyl/webhook", Map.of("reference", reference))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PAIEMENT_INTROUVABLE"));
    }

    private String lire(org.springframework.test.web.servlet.ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString()).get("reference").asText();
    }
}
