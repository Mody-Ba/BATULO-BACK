package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Droits sur les sondages : admin, gestionnaire avec ou sans permission, isolation, abonnement. */
class SondageDroitsIntegrationTest extends PostgresIntegrationTestBase {

    @Test
    void gestionnaireSansPermissionNePeutPasCreer() throws Exception {
        gestionnaire("g@test.com", adminActif("awa@test.com"), false);

        postAvec(jeton("g@test.com"), "/api/sondages", Map.of("titre", "S"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_SONDAGES_REQUISE"));
    }

    @Test
    void gestionnaireNeVoitEtNeModifieQueSesSondages() throws Exception {
        Utilisateur admin = adminActif("awa@test.com");
        gestionnaire("g1@test.com", admin, true);
        gestionnaire("g2@test.com", admin, true);
        String jetonG1 = jeton("g1@test.com");
        String jetonG2 = jeton("g2@test.com");
        long sondageG1 = creer(jetonG1, "Sondage de G1");
        creer(jeton("awa@test.com"), "Sondage de l'admin");

        getAvec(jetonG1, "/api/sondages")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu", hasSize(1)))
                .andExpect(jsonPath("$.contenu[0].creePar.prenom").value("Prénom"));
        getAvec(jetonG2, "/api/sondages").andExpect(jsonPath("$.contenu", hasSize(0)));
        getAvec(jetonG2, "/api/sondages/" + sondageG1)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SONDAGE_INTROUVABLE"));
        putAvec(jetonG2, "/api/sondages/" + sondageG1, Map.of("titre", "Volé")).andExpect(status().isNotFound());
        deleteAvec(jetonG2, "/api/sondages/" + sondageG1).andExpect(status().isNotFound());

        // L'admin voit et modifie tous les sondages de son entreprise.
        String jetonAdmin = jeton("awa@test.com");
        getAvec(jetonAdmin, "/api/sondages").andExpect(jsonPath("$.contenu", hasSize(2)));
        putAvec(jetonAdmin, "/api/sondages/" + sondageG1, Map.of("titre", "Revu par l'admin"))
                .andExpect(status().isOk());
    }

    @Test
    void permissionRetireeLaisseLaLectureMaisPlusLaModification() throws Exception {
        Utilisateur admin = adminActif("awa@test.com");
        Utilisateur gestionnaire = gestionnaire("g@test.com", admin, true);
        String jetonG = jeton("g@test.com");
        long sondageId = creer(jetonG, "Mon sondage");

        patchAvecCorps(jeton("awa@test.com"), "/api/admin/gestionnaires/" + gestionnaire.getId() + "/permission-sondages",
                Map.of("peutCreerSondages", false));

        getAvec(jetonG, "/api/sondages/" + sondageId).andExpect(status().isOk());
        getAvec(jetonG, "/api/sondages").andExpect(jsonPath("$.contenu", hasSize(1)));
        putAvec(jetonG, "/api/sondages/" + sondageId, Map.of("titre", "Modifié"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_SONDAGES_REQUISE"));
        postAvec(jetonG, "/api/sondages/" + sondageId + "/sections", Map.of("titre", "Section"))
                .andExpect(status().isForbidden());
        deleteAvec(jetonG, "/api/sondages/" + sondageId).andExpect(status().isForbidden());
    }

    @Test
    void isolationStricteEntreEntreprises() throws Exception {
        adminActif("a@test.com");
        adminActif("b@test.com");
        String jetonA = jeton("a@test.com");
        String jetonB = jeton("b@test.com");
        long sondageA = creer(jetonA, "Sondage A");
        JsonNode avecSection = objectMapper.readTree(postAvec(jetonA, "/api/sondages/" + sondageA + "/sections",
                Map.of("titre", "Section A")).andReturn().getResponse().getContentAsString());
        long sectionA = avecSection.at("/sections/0/id").asLong();

        getAvec(jetonB, "/api/sondages/" + sondageA).andExpect(status().isNotFound());
        putAvec(jetonB, "/api/sondages/" + sondageA, Map.of("titre", "Piraté")).andExpect(status().isNotFound());
        deleteAvec(jetonB, "/api/sondages/" + sondageA).andExpect(status().isNotFound());
        postAvec(jetonB, "/api/sondages/" + sondageA + "/sections", Map.of("titre", "X")).andExpect(status().isNotFound());
        getAvec(jetonB, "/api/sondages").andExpect(jsonPath("$.contenu", hasSize(0)));

        // Une section d'un autre sondage, même de la même entreprise, est introuvable sous cette URL.
        long sondageB = creer(jetonB, "Sondage B");
        postAvec(jetonB, "/api/sondages/" + sondageB + "/sections/" + sectionA + "/questions",
                SondageIntegrationTest.question("Q", "OUI_NON", null))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SECTION_INTROUVABLE"));
        long sondageA2 = creer(jetonA, "Sondage A2");
        putAvec(jetonA, "/api/sondages/" + sondageA2 + "/sections/" + sectionA, Map.of("titre", "X"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SECTION_INTROUVABLE"));
    }

    @Test
    void lesReglesDAbonnementSAppliquent() throws Exception {
        admin("attente@test.com", null);
        getAvec(jeton("attente@test.com"), "/api/sondages")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ABONNEMENT_EN_ATTENTE_PAIEMENT"));

        Utilisateur expire = adminActif("expire@test.com");
        String jetonExpire = jeton("expire@test.com");
        long sondageId = creer(jetonExpire, "Avant expiration");
        jdbcTemplate.update("update abonnement set date_expiration = ? where entreprise_id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(1))), expire.getEntreprise().getId());

        getAvec(jetonExpire, "/api/sondages/" + sondageId).andExpect(status().isOk());
        postAvec(jetonExpire, "/api/sondages", Map.of("titre", "Après expiration"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ABONNEMENT_EXPIRE"));
    }

    @Test
    void superAdminNAPasAccesAuxSondages() throws Exception {
        superAdmin("proprio@test.com");

        getAvec(jeton("proprio@test.com"), "/api/sondages")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCES_REFUSE"));
    }

    @Test
    void supprimerLeGestionnaireCreateurGardeLeSondage() throws Exception {
        Utilisateur admin = adminActif("awa@test.com");
        Utilisateur gestionnaire = gestionnaire("g@test.com", admin, true);
        long sondageId = creer(jeton("g@test.com"), "Sondage du gestionnaire");
        String jetonAdmin = jeton("awa@test.com");

        deleteAvec(jetonAdmin, "/api/admin/gestionnaires/" + gestionnaire.getId()).andExpect(status().isNoContent());

        getAvec(jetonAdmin, "/api/sondages/" + sondageId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.creePar").doesNotExist());
        getAvec(jetonAdmin, "/api/sondages").andExpect(jsonPath("$.contenu[0].id").value(sondageId));
    }

    private long creer(String jeton, String titre) throws Exception {
        return objectMapper.readTree(postAvec(jeton, "/api/sondages", Map.of("titre", titre))
                        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asLong();
    }

    private void patchAvecCorps(String jeton, String url, Object corps) throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url)
                        .header("Authorization", "Bearer " + jeton)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(json(corps)))
                .andExpect(status().isOk());
    }
}
