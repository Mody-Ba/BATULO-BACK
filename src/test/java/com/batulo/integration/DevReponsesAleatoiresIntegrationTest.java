package com.batulo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Route de développement : réponses aléatoires valides (profil dev uniquement). */
@ActiveProfiles("dev")
class DevReponsesAleatoiresIntegrationTest extends PostgresIntegrationTestBase {

    @Test
    void genereDesReponsesValidesSurLesTrenteDerniersJours() throws Exception {
        adminActif("awa@test.com");
        String jeton = jeton("awa@test.com");
        long id = lire(postAvec(jeton, "/api/sondages", Map.of("titre", "Test"))).get("id").asLong();
        long section = lire(postAvec(jeton, "/api/sondages/" + id + "/sections", Map.of("titre", "S")))
                .at("/sections/0/id").asLong();
        for (Map<String, Object> question : List.of(
                StatistiqueIntegrationTest.q("Région", "CHOIX_UNIQUE", true, null, "Kaolack", "Fatick"),
                StatistiqueIntegrationTest.q("Cultures", "CHOIX_MULTIPLE", false, null, "Arachide", "Mil"),
                StatistiqueIntegrationTest.q("Engrais", "OUI_NON", true, null),
                StatistiqueIntegrationTest.q("Note", "ECHELLE", true, Map.of("max", 10)),
                StatistiqueIntegrationTest.q("Surface", "NUMERIQUE", true, Map.of("min", 0.5, "max", 3, "decimales", 0)),
                StatistiqueIntegrationTest.q("Semis", "DATE", true, Map.of("dateMin", "2026-05-01", "dateMax", "2026-05-31")),
                StatistiqueIntegrationTest.q("Village", "TEXTE_COURT", false, Map.of("longueurMax", 10)),
                StatistiqueIntegrationTest.q("Priorités", "CLASSEMENT", true, null, "A", "B", "C"),
                StatistiqueIntegrationTest.q("Services", "MATRICE", true, Map.of("lignes", List.of("L1", "L2"),
                        "colonnes", List.of("C1", "C2"))))) {
            postAvec(jeton, "/api/sondages/" + id + "/sections/" + section + "/questions", question)
                    .andExpect(status().isCreated());
        }
        String url = "/api/dev/sondages/" + id + "/reponses-aleatoires?nombre=50";

        // Pas encore EN_COURS.
        postAvec(jeton, url, Map.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SONDAGE_NON_EN_COURS"));

        postAvec(jeton, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
        postAvec(jeton, url, Map.of()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("50 réponses de test générées."));

        getAvec(jeton, "/api/sondages/" + id + "/collecte").andExpect(jsonPath("$.nombreReponses").value(50));
        Timestamp plusAncienne = jdbcTemplate.queryForObject("select min(date_soumission) from reponse", Timestamp.class);
        assertThat(plusAncienne.toInstant()).isAfter(Instant.now().minus(Duration.ofDays(30)).minusSeconds(60));

        // Questions obligatoires toujours répondues ; valeurs dans les bornes.
        JsonNode stats = lire(getAvec(jeton, "/api/sondages/" + id + "/statistiques").andExpect(status().isOk()));
        for (JsonNode question : stats.get("questions")) {
            if (List.of("Région", "Engrais", "Note", "Surface", "Semis", "Priorités", "Services")
                    .contains(question.get("libelle").asText())) {
                assertThat(question.get("nombreRepondants").asLong()).as(question.get("libelle").asText()).isEqualTo(50);
            }
            if (question.get("libelle").asText().equals("Surface")) {
                assertThat(question.at("/numerique/min").decimalValue()).isGreaterThanOrEqualTo(java.math.BigDecimal.ONE);
                assertThat(question.at("/numerique/max").decimalValue()).isLessThanOrEqualTo(java.math.BigDecimal.valueOf(3));
            }
        }
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
