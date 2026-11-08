package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Étape 2.3 : assignation des sondages aux gestionnaires (AD-10, GE-04, RG-02, RG-03). */
class AssignationIntegrationTest extends PostgresIntegrationTestBase {

    private Utilisateur admin;
    private String jetonAdmin;
    private Utilisateur g1;
    private Utilisateur g2;

    @BeforeEach
    void preparer() throws Exception {
        admin = adminActif("awa@test.com");
        jetonAdmin = jeton("awa@test.com");
        g1 = gestionnaire("g1@test.com", admin, true);
        g2 = gestionnaire("g2@test.com", admin, true);
    }

    @Test
    void gestionnaireAssigneVoitMaisNeGerePas() throws Exception {
        long id = sondagePublie(jetonAdmin, "Enquête");

        JsonNode assignes = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/assignations",
                Map.of("gestionnaireIds", List.of(g1.getId()))).andExpect(status().isOk()));
        assertThat(assignes.size()).isEqualTo(1);
        assertThat(assignes.get(0).get("gestionnaireId").asLong()).isEqualTo(g1.getId());
        assertThat(assignes.get(0).at("/assignePar/id").asLong()).isEqualTo(admin.getId());
        verify(emailService).envoyerAssignationSondage(eq("g1@test.com"), anyString(), eq("Enquête"), anyString());

        String jetonG1 = jeton("g1@test.com");
        getAvec(jetonG1, "/api/notifications")
                .andExpect(jsonPath("$.contenu[0].type").value("SONDAGE_ASSIGNE"));

        // Voir, prévisualiser, consulter les versions.
        getAvec(jetonG1, "/api/sondages").andExpect(jsonPath("$.contenu", hasSize(1)));
        getAvec(jetonG1, "/api/sondages/" + id).andExpect(status().isOk());
        getAvec(jetonG1, "/api/sondages/" + id + "/apercu").andExpect(status().isOk());
        getAvec(jetonG1, "/api/sondages/" + id + "/versions").andExpect(status().isOk());

        // Ni modifier, ni publier, ni fermer, ni versionner, ni marquer comme modèle.
        String base = "/api/sondages/" + id;
        for (ResultActions refus : List.of(
                putAvec(jetonG1, base, Map.of("titre", "Autre")),
                postAvec(jetonG1, base + "/fermer", Map.of()),
                putAvec(jetonG1, base + "/dates", Map.of()),
                postAvec(jetonG1, base + "/nouvelle-version", Map.of()),
                putAvec(jetonG1, base + "/modele", Map.of("estModele", true)),
                deleteAvec(jetonG1, base))) {
            refus.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACTION_RESERVEE_CREATEUR"));
        }
        // ... mais il peut s'en servir comme source pour créer son propre sondage (il a la permission).
        postAvec(jetonG1, "/api/sondages", Map.of("titre", "Ma copie", "sondageSourceId", id))
                .andExpect(status().isCreated());

        // RG-02 : un autre gestionnaire ne voit rien.
        String jetonG2 = jeton("g2@test.com");
        getAvec(jetonG2, "/api/sondages").andExpect(jsonPath("$.contenu", hasSize(0)));
        getAvec(jetonG2, "/api/sondages/" + id).andExpect(status().isNotFound());
    }

    @Test
    void reassignerNeCreePasDeDoublonEtRetirerNotifieSansCourriel() throws Exception {
        long id = sondagePublie(jetonAdmin, "Enquête");
        String url = "/api/sondages/" + id + "/assignations";
        postAvec(jetonAdmin, url, Map.of("gestionnaireIds", List.of(g1.getId()))).andExpect(status().isOk());

        postAvec(jetonAdmin, url, Map.of("gestionnaireIds", List.of(g1.getId(), g2.getId(), g2.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
        verify(emailService, times(1)).envoyerAssignationSondage(eq("g1@test.com"), anyString(), anyString(), anyString());
        verify(emailService, times(1)).envoyerAssignationSondage(eq("g2@test.com"), anyString(), anyString(), anyString());
        getAvec(jetonAdmin, url).andExpect(jsonPath("$", hasSize(2)));

        deleteAvec(jetonAdmin, url + "/" + g1.getId()).andExpect(status().isNoContent());
        String jetonG1 = jeton("g1@test.com");
        getAvec(jetonG1, "/api/sondages/" + id).andExpect(status().isNotFound());
        getAvec(jetonG1, "/api/notifications")
                .andExpect(jsonPath("$.contenu[0].type").value("SONDAGE_DESASSIGNE"));
        deleteAvec(jetonAdmin, url + "/" + g1.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ASSIGNATION_INTROUVABLE"));
        getAvec(jetonAdmin, url).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void toutesLesVersionsPartagentLesAssignationsMaisPasLesCopies() throws Exception {
        long v1 = sondagePublie(jetonAdmin, "Enquête");
        long v2 = lire(postAvec(jetonAdmin, "/api/sondages/" + v1 + "/nouvelle-version", Map.of())).get("id").asLong();

        // Assigner via l'id de la v2 assigne la lignée.
        postAvec(jetonAdmin, "/api/sondages/" + v2 + "/assignations", Map.of("gestionnaireIds", List.of(g1.getId())))
                .andExpect(status().isOk());
        getAvec(jetonAdmin, "/api/sondages/" + v1 + "/assignations").andExpect(jsonPath("$", hasSize(1)));

        String jetonG1 = jeton("g1@test.com");
        getAvec(jetonG1, "/api/sondages/" + v1).andExpect(status().isOk());
        getAvec(jetonG1, "/api/sondages/" + v2).andExpect(status().isOk());
        getAvec(jetonG1, "/api/sondages").andExpect(jsonPath("$.contenu", hasSize(2)));

        // Une version créée après l'assignation est aussi visible.
        postAvec(jetonAdmin, "/api/sondages/" + v2 + "/publier", Map.of()).andExpect(status().isOk());
        long v3 = lire(postAvec(jetonAdmin, "/api/sondages/" + v2 + "/nouvelle-version", Map.of())).get("id").asLong();
        getAvec(jetonG1, "/api/sondages/" + v3).andExpect(status().isOk());

        // Une copie (réutilisation) ouvre une nouvelle lignée, sans assignation.
        long copie = lire(postAvec(jetonAdmin, "/api/sondages/" + v1 + "/dupliquer", Map.of())).get("id").asLong();
        getAvec(jetonG1, "/api/sondages/" + copie).andExpect(status().isNotFound());
        getAvec(jetonAdmin, "/api/sondages/" + copie + "/assignations").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void visibiliteParLigneePourLeCreateur() throws Exception {
        String jetonG1 = jeton("g1@test.com");
        long v1 = sondagePublie(jetonG1, "Sondage de G1");
        // L'admin crée la v2 à partir du sondage de G1.
        long v2 = lire(postAvec(jetonAdmin, "/api/sondages/" + v1 + "/nouvelle-version", Map.of())).get("id").asLong();

        getAvec(jetonG1, "/api/sondages/" + v2).andExpect(status().isOk());
        getAvec(jetonG1, "/api/sondages").andExpect(jsonPath("$.contenu", hasSize(2)));
        // Droits de modification inchangés : la v2 est celle de l'admin.
        putAvec(jetonG1, "/api/sondages/" + v2, Map.of("titre", "Changé"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_RESERVEE_CREATEUR"));
        // Assigner le créateur de la lignée ne sert à rien.
        postAvec(jetonAdmin, "/api/sondages/" + v2 + "/assignations", Map.of("gestionnaireIds", List.of(g1.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GESTIONNAIRE_CREATEUR"));
    }

    @Test
    void erreursDAssignation() throws Exception {
        long id = sondagePublie(jetonAdmin, "Enquête");
        String url = "/api/sondages/" + id + "/assignations";
        Utilisateur autreAdmin = adminActif("b@test.com");
        Utilisateur gestionnaireB = gestionnaire("gb@test.com", autreAdmin, true);
        Utilisateur desactive = gestionnaire("off@test.com", admin, true);
        desactive.setActif(false);
        utilisateurRepository.save(desactive);

        postAvec(jetonAdmin, url, Map.of("gestionnaireIds", List.of(gestionnaireB.getId())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GESTIONNAIRE_INTROUVABLE"));
        postAvec(jetonAdmin, url, Map.of("gestionnaireIds", List.of(admin.getId())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GESTIONNAIRE_INTROUVABLE"));
        postAvec(jetonAdmin, url, Map.of("gestionnaireIds", List.of(desactive.getId())))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("GESTIONNAIRE_DESACTIVE"));
        postAvec(jetonAdmin, url, Map.of("gestionnaireIds", List.of()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION"));

        // Tout ou rien : une erreur sur un gestionnaire n'assigne personne.
        postAvec(jetonAdmin, url, Map.of("gestionnaireIds", List.of(g1.getId(), desactive.getId())))
                .andExpect(status().isConflict());
        getAvec(jetonAdmin, url).andExpect(jsonPath("$", hasSize(0)));

        // Isolation : l'autre admin ne voit pas le sondage.
        postAvec(jeton("b@test.com"), url, Map.of("gestionnaireIds", List.of(gestionnaireB.getId())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SONDAGE_INTROUVABLE"));
        getAvec(jeton("b@test.com"), "/api/admin/gestionnaires/" + g1.getId() + "/sondages")
                .andExpect(status().isNotFound());
    }

    @Test
    void sondagesDUnGestionnaireAvecLaRelation() throws Exception {
        String jetonG1 = jeton("g1@test.com");
        long cree = sondagePublie(jetonG1, "Créé par G1");
        long assigne = sondagePublie(jetonAdmin, "Assigné à G1");
        sondagePublie(jetonAdmin, "Ni l'un ni l'autre");
        postAvec(jetonAdmin, "/api/sondages/" + assigne + "/assignations", Map.of("gestionnaireIds", List.of(g1.getId())));

        JsonNode page = lire(getAvec(jetonAdmin, "/api/admin/gestionnaires/" + g1.getId() + "/sondages")
                .andExpect(status().isOk()));
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        for (JsonNode sondage : page.get("contenu")) {
            String attendu = sondage.get("id").asLong() == cree ? "CREATEUR" : "ASSIGNE";
            assertThat(sondage.get("relation").asText()).isEqualTo(attendu);
        }
    }

    @Test
    void supprimerLeGestionnaireSupprimeSesAssignations() throws Exception {
        long id = sondagePublie(jetonAdmin, "Enquête");
        postAvec(jetonAdmin, "/api/sondages/" + id + "/assignations", Map.of("gestionnaireIds", List.of(g1.getId())));

        deleteAvec(jetonAdmin, "/api/admin/gestionnaires/" + g1.getId()).andExpect(status().isNoContent());

        getAvec(jetonAdmin, "/api/sondages/" + id + "/assignations").andExpect(jsonPath("$", hasSize(0)));
        assertThat(jdbcTemplate.queryForObject("select count(*) from assignation", Long.class)).isZero();
    }

    @Test
    void rolesEtAbonnement() throws Exception {
        long id = sondagePublie(jetonAdmin, "Enquête");
        String url = "/api/sondages/" + id + "/assignations";

        postAvec(jeton("g1@test.com"), url, Map.of("gestionnaireIds", List.of(g2.getId())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCES_REFUSE"));
        getAvec(jeton("g1@test.com"), url)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCES_REFUSE"));

        jdbcTemplate.update("update abonnement set date_expiration = ? where entreprise_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1))), admin.getEntreprise().getId());
        postAvec(jetonAdmin, url, Map.of("gestionnaireIds", List.of(g1.getId())))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ABONNEMENT_EXPIRE"));
        getAvec(jetonAdmin, url).andExpect(status().isOk());
    }

    /** Sondage publié avec une section et une question. */
    private long sondagePublie(String jeton, String titre) throws Exception {
        long id = lire(postAvec(jeton, "/api/sondages", Map.of("titre", titre)).andExpect(status().isCreated()))
                .get("id").asLong();
        long sectionId = lire(postAvec(jeton, "/api/sondages/" + id + "/sections", Map.of("titre", "Section")))
                .at("/sections/0/id").asLong();
        postAvec(jeton, "/api/sondages/" + id + "/sections/" + sectionId + "/questions",
                SondageIntegrationTest.question("Q", "OUI_NON", null)).andExpect(status().isCreated());
        postAvec(jeton, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
        return id;
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
