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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Étape 2.2 : statuts calculés, publication, fermeture, gel du contenu, versions, duplication, aperçu. */
class SondagePublicationIntegrationTest extends PostgresIntegrationTestBase {

    private String jeton;

    @BeforeEach
    void connecterAdmin() throws Exception {
        adminActif("awa@test.com");
        jeton = jeton("awa@test.com");
    }

    @Test
    void cycleBrouillonPublieFermeEtContenuGele() throws Exception {
        JsonNode brouillon = sondageComplet("Enquête");
        long id = brouillon.get("id").asLong();
        long sectionId = brouillon.at("/sections/0/id").asLong();
        long questionId = brouillon.at("/sections/0/questions/0/id").asLong();
        assertThat(brouillon.get("statut").asText()).isEqualTo("NON_COMMENCE");
        assertThat(brouillon.get("publie").asBoolean()).isFalse();
        assertThat(brouillon.get("version").asInt()).isEqualTo(1);

        JsonNode publie = lire(postAvec(jeton, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk()));
        assertThat(publie.get("statut").asText()).isEqualTo("EN_COURS");
        assertThat(publie.get("publie").asBoolean()).isTrue();
        assertThat(publie.get("datePublication").isNull()).isFalse();

        // Contenu gelé : toute modification est refusée, la suppression aussi.
        String base = "/api/sondages/" + id;
        for (ResultActions refus : List.of(
                putAvec(jeton, base, Map.of("titre", "Autre")),
                postAvec(jeton, base + "/sections", Map.of("titre", "S2")),
                putAvec(jeton, base + "/sections/" + sectionId, Map.of("titre", "S")),
                postAvec(jeton, base + "/sections/" + sectionId + "/questions",
                        SondageIntegrationTest.question("Q", "OUI_NON", null)),
                putAvec(jeton, base + "/questions/" + questionId, SondageIntegrationTest.question("Q", "OUI_NON", null)),
                deleteAvec(jeton, base + "/questions/" + questionId),
                putAvec(jeton, base + "/sections/ordre", Map.of("ids", List.of(sectionId))),
                deleteAvec(jeton, base))) {
            refus.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SONDAGE_PUBLIE"));
        }
        postAvec(jeton, base + "/publier", Map.of()).andExpect(status().isConflict());

        JsonNode ferme = lire(postAvec(jeton, base + "/fermer", Map.of()).andExpect(status().isOk()));
        assertThat(ferme.get("statut").asText()).isEqualTo("TERMINE");
        postAvec(jeton, base + "/fermer", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SONDAGE_TERMINE"));
        putAvec(jeton, base + "/dates", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SONDAGE_TERMINE"));
        getAvec(jeton, base).andExpect(status().isOk());
    }

    @Test
    void publicationPlanifieeEtAjustementDesDates() throws Exception {
        long id = sondageComplet("Planifié").get("id").asLong();
        Instant ouverture = Instant.now().plus(Duration.ofDays(2));
        Instant fermeture = Instant.now().plus(Duration.ofDays(10));

        JsonNode planifie = lire(postAvec(jeton, "/api/sondages/" + id + "/publier",
                Map.of("datePublication", ouverture.toString(), "dateFermeture", fermeture.toString()))
                .andExpect(status().isOk()));
        assertThat(planifie.get("statut").asText()).isEqualTo("NON_COMMENCE");
        assertThat(planifie.get("publie").asBoolean()).isTrue();

        // Avant l'ouverture, les deux dates peuvent changer.
        Instant nouvelleOuverture = Instant.now().plus(Duration.ofDays(1));
        putAvec(jeton, "/api/sondages/" + id + "/dates",
                Map.of("datePublication", nouvelleOuverture.toString(), "dateFermeture", fermeture.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("NON_COMMENCE"));

        // Une fois ouvert, l'ouverture ne change plus ; la fermeture peut être prolongée.
        dater(id, "date_publication", Instant.now().minus(Duration.ofHours(1)));
        getAvec(jeton, "/api/sondages/" + id).andExpect(jsonPath("$.statut").value("EN_COURS"));
        putAvec(jeton, "/api/sondages/" + id + "/dates",
                Map.of("datePublication", nouvelleOuverture.toString(), "dateFermeture", fermeture.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATES_INVALIDES"));
        Instant prolongee = Instant.now().plus(Duration.ofDays(30));
        putAvec(jeton, "/api/sondages/" + id + "/dates", Map.of("dateFermeture", prolongee.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_COURS"));

        // Date de fermeture atteinte : terminé, sans tâche planifiée.
        dater(id, "date_fermeture", Instant.now().minus(Duration.ofMinutes(1)));
        getAvec(jeton, "/api/sondages/" + id).andExpect(jsonPath("$.statut").value("TERMINE"));
    }

    @Test
    void publicationRefuseeSiIncompleteOuDatesIncoherentes() throws Exception {
        long vide = lire(postAvec(jeton, "/api/sondages", Map.of("titre", "Vide"))).get("id").asLong();
        postAvec(jeton, "/api/sondages/" + vide + "/publier", Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SONDAGE_INCOMPLET"));
        postAvec(jeton, "/api/sondages/" + vide + "/sections", Map.of("titre", "Sans question"));
        postAvec(jeton, "/api/sondages/" + vide + "/publier", Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SONDAGE_INCOMPLET"));

        long id = sondageComplet("Dates").get("id").asLong();
        Instant ouverture = Instant.now().plus(Duration.ofDays(5));
        postAvec(jeton, "/api/sondages/" + id + "/publier", Map.of("datePublication", ouverture.toString(),
                "dateFermeture", ouverture.minus(Duration.ofDays(1)).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("DATES_INVALIDES"));

        // Brouillon : ni fermeture ni ajustement de dates.
        postAvec(jeton, "/api/sondages/" + id + "/fermer", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SONDAGE_BROUILLON"));
        putAvec(jeton, "/api/sondages/" + id + "/dates", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SONDAGE_BROUILLON"));
        // Un brouillon se supprime.
        deleteAvec(jeton, "/api/sondages/" + id).andExpect(status().isNoContent());
    }

    @Test
    void nouvelleVersionPuisPublicationFermeLaPrecedente() throws Exception {
        JsonNode brouillon = sondageComplet("Enquête");
        long v1 = brouillon.get("id").asLong();
        postAvec(jeton, "/api/sondages/" + v1 + "/nouvelle-version", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SONDAGE_BROUILLON"));
        postAvec(jeton, "/api/sondages/" + v1 + "/publier", Map.of()).andExpect(status().isOk());

        JsonNode version2 = lire(postAvec(jeton, "/api/sondages/" + v1 + "/nouvelle-version", Map.of())
                .andExpect(status().isCreated()));
        long v2 = version2.get("id").asLong();
        assertThat(version2.get("version").asInt()).isEqualTo(2);
        assertThat(version2.get("sondageOrigineId").asLong()).isEqualTo(v1);
        assertThat(version2.get("publie").asBoolean()).isFalse();
        assertThat(version2.get("titre").asText()).isEqualTo("Enquête");
        assertMemeContenu(brouillon, version2);

        postAvec(jeton, "/api/sondages/" + v1 + "/nouvelle-version", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BROUILLON_EXISTANT"));

        // La v2 se modifie, puis sa publication ferme la v1.
        putAvec(jeton, "/api/sondages/" + v2, Map.of("titre", "Enquête (révisée)")).andExpect(status().isOk());
        postAvec(jeton, "/api/sondages/" + v2 + "/publier", Map.of()).andExpect(status().isOk());
        getAvec(jeton, "/api/sondages/" + v1).andExpect(jsonPath("$.statut").value("TERMINE"));
        getAvec(jeton, "/api/sondages/" + v2).andExpect(jsonPath("$.statut").value("EN_COURS"));

        postAvec(jeton, "/api/sondages/" + v1 + "/nouvelle-version", Map.of())
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_OBSOLETE"));
        getAvec(jeton, "/api/sondages/" + v2 + "/versions")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(v1))
                .andExpect(jsonPath("$[0].statut").value("TERMINE"))
                .andExpect(jsonPath("$[1].version").value(2));

        // La v3 est issue de la v2 et reste dans la lignée de la v1.
        JsonNode version3 = lire(postAvec(jeton, "/api/sondages/" + v2 + "/nouvelle-version", Map.of())
                .andExpect(status().isCreated()));
        assertThat(version3.get("version").asInt()).isEqualTo(3);
        assertThat(version3.get("sondageOrigineId").asLong()).isEqualTo(v2);
        getAvec(jeton, "/api/sondages/" + version3.get("id").asLong() + "/versions")
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(v1));
    }

    @Test
    void versionPlanifieeFermeLaPrecedenteASonOuverture() throws Exception {
        long v1 = sondageComplet("Enquête").get("id").asLong();
        postAvec(jeton, "/api/sondages/" + v1 + "/publier", Map.of()).andExpect(status().isOk());
        long v2 = lire(postAvec(jeton, "/api/sondages/" + v1 + "/nouvelle-version", Map.of())).get("id").asLong();
        Instant ouvertureV2 = Instant.now().plus(Duration.ofDays(3));

        postAvec(jeton, "/api/sondages/" + v2 + "/publier", Map.of("datePublication", ouvertureV2.toString()))
                .andExpect(status().isOk());

        // La v1 reste ouverte jusqu'à l'ouverture de la v2 : jamais deux versions ouvertes, jamais de trou.
        JsonNode version1 = lire(getAvec(jeton, "/api/sondages/" + v1));
        assertThat(version1.get("statut").asText()).isEqualTo("EN_COURS");
        assertThat(Instant.parse(version1.get("dateFermeture").asText())).isEqualTo(ouvertureV2);
    }

    @Test
    void dupliquerCreeUnBrouillonIndependant() throws Exception {
        JsonNode source = sondageComplet("Enquête");
        long id = source.get("id").asLong();
        postAvec(jeton, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());

        JsonNode copie = lire(postAvec(jeton, "/api/sondages/" + id + "/dupliquer", Map.of())
                .andExpect(status().isCreated()));
        assertThat(copie.get("id").asLong()).isNotEqualTo(id);
        assertThat(copie.get("titre").asText()).isEqualTo("Copie de Enquête");
        assertThat(copie.get("version").asInt()).isEqualTo(1);
        assertThat(copie.get("sondageOrigineId").asLong()).isEqualTo(id);
        assertThat(copie.get("publie").asBoolean()).isFalse();
        assertMemeContenu(source, copie);

        // La copie est modifiable, la source reste intacte.
        putAvec(jeton, "/api/sondages/" + copie.get("id").asLong(), Map.of("titre", "Copie modifiée"))
                .andExpect(status().isOk());
        getAvec(jeton, "/api/sondages/" + id).andExpect(jsonPath("$.titre").value("Enquête"));
    }

    @Test
    void apercuSansInformationsInternes() throws Exception {
        long id = sondageComplet("Aperçu").get("id").asLong();

        JsonNode apercu = lire(getAvec(jeton, "/api/sondages/" + id + "/apercu").andExpect(status().isOk()));
        assertThat(apercu.get("titre").asText()).isEqualTo("Aperçu");
        assertThat(apercu.get("statut").asText()).isEqualTo("NON_COMMENCE");
        assertThat(apercu.at("/sections/0/questions/0/options").size()).isEqualTo(2);
        assertThat(apercu.has("creePar")).isFalse();
        assertThat(apercu.has("entrepriseId")).isFalse();
        assertThat(apercu.has("dateCreation")).isFalse();
    }

    @Test
    void filtreParStatut() throws Exception {
        sondageComplet("Brouillon");
        long enCours = sondageComplet("En cours").get("id").asLong();
        postAvec(jeton, "/api/sondages/" + enCours + "/publier", Map.of());
        long termine = sondageComplet("Terminé").get("id").asLong();
        postAvec(jeton, "/api/sondages/" + termine + "/publier", Map.of());
        postAvec(jeton, "/api/sondages/" + termine + "/fermer", Map.of());
        long planifie = sondageComplet("Planifié").get("id").asLong();
        postAvec(jeton, "/api/sondages/" + planifie + "/publier",
                Map.of("datePublication", Instant.now().plus(Duration.ofDays(1)).toString()));

        getAvec(jeton, "/api/sondages?statut=NON_COMMENCE").andExpect(jsonPath("$.totalElements").value(2));
        getAvec(jeton, "/api/sondages?statut=EN_COURS")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].id").value(enCours));
        getAvec(jeton, "/api/sondages?statut=TERMINE")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].statut").value("TERMINE"));
        getAvec(jeton, "/api/sondages").andExpect(jsonPath("$.totalElements").value(4));
        getAvec(jeton, "/api/sondages?statut=OUVERT").andExpect(status().isBadRequest());
    }

    @Test
    void gestionnaireSansPermissionNePublieNiNeDuplique() throws Exception {
        Utilisateur admin = utilisateurRepository.findByCourriel("awa@test.com").orElseThrow();
        Utilisateur gestionnaire = gestionnaire("g@test.com", admin, true);
        String jetonG = jeton("g@test.com");
        long id = lire(postAvec(jetonG, "/api/sondages", Map.of("titre", "Le mien"))).get("id").asLong();
        gestionnaire.setPeutCreerSondages(false);
        utilisateurRepository.save(gestionnaire);

        for (String action : List.of("publier", "dupliquer", "fermer", "nouvelle-version")) {
            postAvec(jetonG, "/api/sondages/" + id + "/" + action, Map.of())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("PERMISSION_SONDAGES_REQUISE"));
        }
        getAvec(jetonG, "/api/sondages/" + id + "/apercu").andExpect(status().isOk());
        getAvec(jetonG, "/api/sondages/" + id + "/versions").andExpect(status().isOk());

        // Une autre entreprise ne voit rien.
        adminActif("b@test.com");
        getAvec(jeton("b@test.com"), "/api/sondages/" + id + "/apercu").andExpect(status().isNotFound());
        postAvec(jeton("b@test.com"), "/api/sondages/" + id + "/dupliquer", Map.of()).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- utilitaires

    /** Sondage avec une section et deux questions (dont une à options) : publiable. */
    private JsonNode sondageComplet(String titre) throws Exception {
        long id = lire(postAvec(jeton, "/api/sondages", Map.of("titre", titre, "description", "Description"))
                .andExpect(status().isCreated())).get("id").asLong();
        long sectionId = lire(postAvec(jeton, "/api/sondages/" + id + "/sections", Map.of("titre", "Section")))
                .at("/sections/0/id").asLong();
        postAvec(jeton, "/api/sondages/" + id + "/sections/" + sectionId + "/questions",
                SondageIntegrationTest.question("Région", "CHOIX_UNIQUE", null, "Kaolack", "Fatick"))
                .andExpect(status().isCreated());
        return lire(postAvec(jeton, "/api/sondages/" + id + "/sections/" + sectionId + "/questions",
                SondageIntegrationTest.question("Satisfaction", "ECHELLE", Map.of("max", 10)))
                .andExpect(status().isCreated()));
    }

    /** Même structure, mêmes libellés, configurations et valeurs ; ids différents. */
    private static void assertMemeContenu(JsonNode source, JsonNode copie) {
        JsonNode sectionsSource = source.get("sections");
        JsonNode sectionsCopie = copie.get("sections");
        assertThat(sectionsCopie.size()).isEqualTo(sectionsSource.size());
        for (int s = 0; s < sectionsSource.size(); s++) {
            JsonNode questionsSource = sectionsSource.get(s).get("questions");
            JsonNode questionsCopie = sectionsCopie.get(s).get("questions");
            assertThat(sectionsCopie.get(s).get("id")).isNotEqualTo(sectionsSource.get(s).get("id"));
            assertThat(questionsCopie.size()).isEqualTo(questionsSource.size());
            for (int q = 0; q < questionsSource.size(); q++) {
                JsonNode qs = questionsSource.get(q);
                JsonNode qc = questionsCopie.get(q);
                assertThat(qc.get("id")).isNotEqualTo(qs.get("id"));
                assertThat(qc.get("libelle")).isEqualTo(qs.get("libelle"));
                assertThat(qc.get("type")).isEqualTo(qs.get("type"));
                assertThat(qc.get("ordre")).isEqualTo(qs.get("ordre"));
                assertThat(qc.get("configuration")).isEqualTo(qs.get("configuration"));
                assertThat(qc.get("options").size()).isEqualTo(qs.get("options").size());
                for (int o = 0; o < qs.get("options").size(); o++) {
                    assertThat(qc.get("options").get(o).get("valeur")).isEqualTo(qs.get("options").get(o).get("valeur"));
                    assertThat(qc.get("options").get(o).get("id")).isNotEqualTo(qs.get("options").get(o).get("id"));
                }
            }
        }
    }

    private void dater(long sondageId, String colonne, Instant date) {
        jdbcTemplate.update("update sondage set " + colonne + " = ? where id = ?", Timestamp.from(date), sondageId);
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
