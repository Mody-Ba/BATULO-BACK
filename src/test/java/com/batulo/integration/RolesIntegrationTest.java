package com.batulo.integration;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 403 ACCES_REFUSE quand le rôle ne correspond pas au préfixe de la route. */
class RolesIntegrationTest extends IntegrationTestBase {

    @Test
    void adminRefuseSurLEspaceSuperAdmin() throws Exception {
        adminActif("awa@test.com");

        getAvec(jeton("awa@test.com"), "/api/super-admin/admins")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));
    }

    @Test
    void mauvaisRoleRefuseMemeAvecUnAbonnementEnAttente() throws Exception {
        admin("attente@test.com", null);

        getAvec(jeton("attente@test.com"), "/api/super-admin/admins")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));
    }

    @Test
    void gestionnaireRefuseSurLEspaceAdminEtLesAbonnements() throws Exception {
        gestionnaire("gora@test.com", adminActif("awa@test.com"));
        String jeton = jeton("gora@test.com");

        getAvec(jeton, "/api/admin/gestionnaires")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCES_REFUSE"));
        getAvec(jeton, "/api/abonnements/moi")
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCES_REFUSE"));
    }

    @Test
    void superAdminRefuseSurLEspaceAdmin() throws Exception {
        superAdmin("proprio@test.com");

        postAvec(jeton("proprio@test.com"), "/api/admin/gestionnaires",
                Map.of("prenom", "A", "nom", "B", "courriel", "x@test.com"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));
    }

    @Test
    void chaqueRoleAccedeASonEspaceEtAuxNotifications() throws Exception {
        superAdmin("proprio@test.com");
        gestionnaire("gora@test.com", adminActif("awa@test.com"));

        getAvec(jeton("proprio@test.com"), "/api/super-admin/abonnements").andExpect(status().isOk());
        getAvec(jeton("awa@test.com"), "/api/admin/gestionnaires").andExpect(status().isOk());
        for (String courriel : new String[]{"proprio@test.com", "awa@test.com", "gora@test.com"}) {
            getAvec(jeton(courriel), "/api/notifications").andExpect(status().isOk());
        }
    }

    @Test
    void routesDeDeveloppementAbsentesHorsProfilDev() throws Exception {
        superAdmin("proprio@test.com");

        postAvec(jeton("proprio@test.com"), "/api/dev/taches/abonnements", Map.of())
                .andExpect(status().isNotFound());
    }
}
