package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Règles d'abonnement appliquées par AccesAbonnementInterceptor (RG-01, RG-08). */
class AccesAbonnementIntegrationTest extends IntegrationTestBase {

    @Test
    void abonnementEnAttenteNePermetQueLAuthEtLesAbonnements() throws Exception {
        admin("attente@test.com", null);
        String jeton = jeton("attente@test.com");

        getAvec(jeton, "/api/admin/gestionnaires")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ABONNEMENT_EN_ATTENTE_PAIEMENT"));
        getAvec(jeton, "/api/notifications")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ABONNEMENT_EN_ATTENTE_PAIEMENT"));
        getAvec(jeton, "/api/abonnements/moi")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_PAIEMENT"));
        getAvec(jeton, "/api/auth/moi").andExpect(status().isOk());
    }

    @Test
    void abonnementExpireEstEnLectureSeule() throws Exception {
        admin("expire@test.com", Instant.now().minus(Duration.ofDays(1)));
        String jeton = jeton("expire@test.com");

        getAvec(jeton, "/api/admin/gestionnaires").andExpect(status().isOk());
        postAvec(jeton, "/api/admin/gestionnaires", Map.of("prenom", "A", "nom", "B", "courriel", "x@test.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ABONNEMENT_EXPIRE"));
        getAvec(jeton, "/api/abonnements/moi").andExpect(status().isOk());
    }

    @Test
    void adminDesactiveBloqueImmediatementSesGestionnaires() throws Exception {
        superAdmin("proprio@test.com");
        Utilisateur admin = adminActif("awa@test.com");
        gestionnaire("gora@test.com", admin);
        String jetonAdmin = jeton("awa@test.com");
        String jetonGestionnaire = jeton("gora@test.com");
        getAvec(jetonGestionnaire, "/api/notifications").andExpect(status().isOk());

        patchAvec(jeton("proprio@test.com"), "/api/super-admin/admins/" + admin.getId() + "/desactiver")
                .andExpect(status().isOk());

        // Les jetons déjà délivrés ne servent plus : le filtre relit le compte à chaque requête.
        getAvec(jetonAdmin, "/api/admin/gestionnaires")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NON_AUTHENTIFIE"));
        getAvec(jetonGestionnaire, "/api/notifications")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ABONNEMENT_DESACTIVE"));
        mockMvc.perform(post("/api/auth/connexion").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("courriel", "gora@test.com", "motDePasse", MOT_DE_PASSE))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMPTE_DESACTIVE"));

        patchAvec(jeton("proprio@test.com"), "/api/super-admin/admins/" + admin.getId() + "/activer")
                .andExpect(status().isOk());
        getAvec(jetonGestionnaire, "/api/notifications").andExpect(status().isOk());
    }

    @Test
    void gestionnaireDesactiveParSonAdminEstBloque() throws Exception {
        Utilisateur admin = adminActif("awa@test.com");
        Utilisateur gestionnaire = gestionnaire("gora@test.com", admin);
        String jetonGestionnaire = jeton("gora@test.com");

        patchAvec(jeton("awa@test.com"), "/api/admin/gestionnaires/" + gestionnaire.getId() + "/desactiver")
                .andExpect(status().isOk());

        getAvec(jetonGestionnaire, "/api/notifications").andExpect(status().isUnauthorized());
    }

    @Test
    void leSuperAdminNEstPasConcerneParLesAbonnements() throws Exception {
        superAdmin("proprio@test.com");

        getAvec(jeton("proprio@test.com"), "/api/super-admin/admins").andExpect(status().isOk());
    }
}
