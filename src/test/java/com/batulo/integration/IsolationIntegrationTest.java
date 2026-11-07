package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un admin ne voit et ne modifie que les données de sa propre entreprise. */
class IsolationIntegrationTest extends IntegrationTestBase {

    @Test
    void unAdminNAccedePasAuxGestionnairesDUneAutreEntreprise() throws Exception {
        Utilisateur adminA = adminActif("a@test.com");
        Utilisateur adminB = adminActif("b@test.com");
        gestionnaire("ga@test.com", adminA);
        Utilisateur gestionnaireB = gestionnaire("gb@test.com", adminB);
        String jetonA = jeton("a@test.com");
        String urlB = "/api/admin/gestionnaires/" + gestionnaireB.getId();

        getAvec(jetonA, "/api/admin/gestionnaires")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu", hasSize(1)))
                .andExpect(jsonPath("$.contenu[0].courriel").value("ga@test.com"));
        getAvec(jetonA, urlB)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GESTIONNAIRE_INTROUVABLE"));
        putAvec(jetonA, urlB, Map.of("prenom", "X", "nom", "Y", "courriel", "pirate@test.com"))
                .andExpect(status().isNotFound());
        patchAvec(jetonA, urlB + "/desactiver").andExpect(status().isNotFound());
        deleteAvec(jetonA, urlB).andExpect(status().isNotFound());

        getAvec(jeton("b@test.com"), urlB)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courriel").value("gb@test.com"))
                .andExpect(jsonPath("$.actif").value(true));
    }

    @Test
    void monAbonnementEstCeluiDeMonEntreprise() throws Exception {
        Utilisateur adminA = adminActif("a@test.com");
        adminActif("b@test.com");

        getAvec(jeton("a@test.com"), "/api/abonnements/moi")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entrepriseId").value(adminA.getEntreprise().getId()));
    }

    @Test
    void unUtilisateurNeMarquePasLesNotificationsDUnAutre() throws Exception {
        Utilisateur adminA = adminActif("a@test.com");
        adminActif("b@test.com");
        jdbcTemplate.update("insert into notification(destinataire_id, type, titre, message, lue, date_creation) "
                + "values (?, 'ABONNEMENT_EXPIRE_BIENTOT', 't', 'm', false, current_timestamp)", adminA.getId());
        Long notificationId = jdbcTemplate.queryForObject("select max(id) from notification", Long.class);

        patchAvec(jeton("b@test.com"), "/api/notifications/" + notificationId + "/lue")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_INTROUVABLE"));
        getAvec(jeton("a@test.com"), "/api/notifications/non-lues/nombre")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nonLues").value(1));
    }
}
