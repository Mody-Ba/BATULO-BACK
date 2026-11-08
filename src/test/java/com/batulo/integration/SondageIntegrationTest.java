package com.batulo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Construction d'un formulaire dynamique : structure, ordre, validation par type, options (module 4.1). */
class SondageIntegrationTest extends PostgresIntegrationTestBase {

    private String jeton;

    @BeforeEach
    void connecterAdmin() throws Exception {
        adminActif("awa@test.com");
        jeton = jeton("awa@test.com");
    }

    @Test
    void construireUnSondageAvecLesDixTypesDeQuestions() throws Exception {
        long sondageId = creerSondage("Enquête agricole");
        long exploitation = ajouterSection(sondageId, "Exploitation");
        long avis = ajouterSection(sondageId, "Avis");

        ajouterQuestion(sondageId, exploitation, question("Région", "CHOIX_UNIQUE", Map.of(), "Kaolack", "Fatick"));
        ajouterQuestion(sondageId, exploitation, question("Cultures", "CHOIX_MULTIPLE", null, "Arachide", "Mil", "Maïs"));
        ajouterQuestion(sondageId, exploitation, question("Engrais ?", "OUI_NON", null));
        ajouterQuestion(sondageId, exploitation, question("Surface (ha)", "NUMERIQUE", Map.of("min", 0, "max", 500)));
        ajouterQuestion(sondageId, exploitation, question("Semis", "DATE",
                Map.of("dateMin", "2026-05-01", "dateMax", "2026-09-30")));
        ajouterQuestion(sondageId, avis, question("Village", "TEXTE_COURT", null));
        ajouterQuestion(sondageId, avis, question("Difficultés", "TEXTE_LONG", Map.of("longueurMax", 3000)));
        ajouterQuestion(sondageId, avis, question("Satisfaction", "ECHELLE",
                Map.of("min", 1, "max", 10, "libelleMin", "Pas du tout", "libelleMax", "Tout à fait")));
        ajouterQuestion(sondageId, avis, question("Priorités", "CLASSEMENT", null, "Semences", "Engrais", "Crédit"));
        JsonNode sondage = ajouterQuestion(sondageId, avis, question("Services", "MATRICE",
                Map.of("lignes", List.of("Vulgarisation", "Marché"), "colonnes", List.of("Mauvais", "Moyen", "Bon"))));

        JsonNode detail = lire(getAvec(jeton, "/api/sondages/" + sondageId).andExpect(status().isOk()));
        assertThat(detail).isEqualTo(sondage);
        assertThat(detail.at("/sections/0/titre").asText()).isEqualTo("Exploitation");
        assertThat(detail.at("/sections/1/ordre").asInt()).isEqualTo(2);
        assertThat(detail.at("/sections/0/questions").size()).isEqualTo(5);
        assertThat(detail.at("/sections/1/questions/4/ordre").asInt()).isEqualTo(5);
        // Valeurs générées depuis le libellé, sans accent.
        assertThat(detail.at("/sections/0/questions/1/options/2/valeur").asText()).isEqualTo("mais");
        // Configurations normalisées : valeurs par défaut ajoutées.
        assertThat(detail.at("/sections/0/questions/3/configuration/decimales").asInt()).isZero();
        assertThat(detail.at("/sections/1/questions/0/configuration/longueurMax").asInt()).isEqualTo(255);
        assertThat(detail.at("/sections/1/questions/2/configuration/max").asInt()).isEqualTo(10);
        assertThat(detail.at("/sections/1/questions/4/configuration/colonnes").size()).isEqualTo(3);
        assertThat(detail.at("/sections/0/questions/2/options").size()).isZero();

        // La configuration est bien un objet JSONB en base.
        assertThat(jdbcTemplate.queryForList("select distinct jsonb_typeof(configuration) from question", String.class))
                .containsExactly("object");

        getAvec(jeton, "/api/sondages")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu[0].nombreSections").value(2))
                .andExpect(jsonPath("$.contenu[0].nombreQuestions").value(10));
    }

    static Stream<Object[]> questionsInvalides() {
        return Stream.of(
                new Object[]{question("Q", "CHOIX_UNIQUE", null, "Seule"), "au moins 2 options"},
                new Object[]{question("Q", "CLASSEMENT", null), "au moins 2 options"},
                new Object[]{question("Q", "OUI_NON", null, "Oui", "Non"), "n'a pas d'options"},
                new Object[]{question("Q", "CHOIX_MULTIPLE", Map.of("max", 2), "A", "B"), "pas de configuration"},
                new Object[]{question("Q", "ECHELLE", Map.of("min", 1, "max", 7)), "1 à 5 ou de 1 à 10"},
                new Object[]{question("Q", "ECHELLE", Map.of("min", 0, "max", 5)), "1 à 5 ou de 1 à 10"},
                new Object[]{question("Q", "NUMERIQUE", Map.of("min", 10, "max", 5)), "min doit être inférieur"},
                new Object[]{question("Q", "NUMERIQUE", Map.of("decimales", 6)), "decimales"},
                new Object[]{question("Q", "NUMERIQUE", Map.of("min", "dix")), "min doit être un nombre"},
                new Object[]{question("Q", "DATE", Map.of("dateMin", "2026-12-01", "dateMax", "2026-01-01")), "dateMin"},
                new Object[]{question("Q", "DATE", Map.of("dateMin", "01/12/2026")), "AAAA-MM-JJ"},
                new Object[]{question("Q", "TEXTE_COURT", Map.of("longueurMax", 300)), "entre 1 et 255"},
                new Object[]{question("Q", "TEXTE_LONG", Map.of("longueurMax", 0)), "entre 1 et 5000"},
                new Object[]{question("Q", "TEXTE_COURT", Map.of("couleur", "rouge")), "Clé de configuration inconnue"},
                new Object[]{question("Q", "MATRICE", Map.of("lignes", List.of("L1"), "colonnes", List.of("C1"))),
                        "entre 2 et 10"},
                new Object[]{question("Q", "MATRICE", Map.of("lignes", List.of("L1", "l1"), "colonnes", List.of("A", "B"))),
                        "deux fois"},
                new Object[]{question("Q", "MATRICE", Map.of("colonnes", List.of("A", "B"))), "exige « lignes »"});
    }

    @ParameterizedTest
    @MethodSource("questionsInvalides")
    void configurationEtOptionsValideesSelonLeType(Map<String, Object> question, String message) throws Exception {
        long sondageId = creerSondage("S");
        long sectionId = ajouterSection(sondageId, "Section");

        postAvec(jeton, "/api/sondages/" + sondageId + "/sections/" + sectionId + "/questions", question)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUESTION_INVALIDE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(message)));
    }

    @Test
    void configurationQuiNEstPasUnObjetRefusee() throws Exception {
        long sondageId = creerSondage("S");
        long sectionId = ajouterSection(sondageId, "Section");
        Map<String, Object> question = new LinkedHashMap<>(question("Q", "TEXTE_COURT", null));
        question.put("configuration", List.of(1, 2));

        postAvec(jeton, "/api/sondages/" + sondageId + "/sections/" + sectionId + "/questions", question)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUESTION_INVALIDE"));
    }

    @Test
    void valeursDOptionsUniquesGenereesEtJamaisRegenerees() throws Exception {
        long sondageId = creerSondage("S");
        long sectionId = ajouterSection(sondageId, "Section");
        String url = "/api/sondages/" + sondageId + "/sections/" + sectionId + "/questions";

        // Valeur donnée deux fois : refusée.
        Map<String, Object> enDouble = question("Q", "CHOIX_UNIQUE", null);
        enDouble.put("options", List.of(Map.of("libelle", "A", "valeur", "x"), Map.of("libelle", "B", "valeur", "x")));
        postAvec(jeton, url, enDouble)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALEUR_OPTION_EN_DOUBLE"));

        // Deux libellés qui donnent la même valeur : suffixe automatique.
        JsonNode sondage = ajouterQuestion(sondageId, sectionId, question("Céréale", "CHOIX_UNIQUE", null, "Maïs", "Mais", "Mil"));
        JsonNode options = sondage.at("/sections/0/questions/0/options");
        assertThat(List.of(options.get(0).get("valeur").asText(), options.get(1).get("valeur").asText()))
                .containsExactly("mais", "mais_2");
        long questionId = sondage.at("/sections/0/questions/0/id").asLong();
        long idMais = options.get(0).get("id").asLong();
        long idMais2 = options.get(1).get("id").asLong();

        // Libellé renommé avec son id : la valeur ne change pas. « Mil » est retiré, une option est ajoutée avec
        // la valeur de l'option retirée, et deux valeurs sont échangées (contrainte unique respectée).
        Map<String, Object> modification = question("Céréale", "CHOIX_UNIQUE", null);
        modification.put("options", List.of(
                Map.of("id", idMais, "libelle", "Maïs blanc"),
                Map.of("id", idMais2, "libelle", "Maïs jaune", "valeur", "mais"),
                Map.of("libelle", "Sorgho", "valeur", "mil")));
        // idMais garde « mais » mais idMais2 le demande : refus.
        putAvec(jeton, "/api/sondages/" + sondageId + "/questions/" + questionId, modification)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALEUR_OPTION_EN_DOUBLE"));

        modification.put("options", List.of(
                Map.of("id", idMais, "libelle", "Maïs blanc", "valeur", "mais_2"),
                Map.of("id", idMais2, "libelle", "Maïs jaune", "valeur", "mais"),
                Map.of("libelle", "Sorgho", "valeur", "mil")));
        JsonNode apres = lire(putAvec(jeton, "/api/sondages/" + sondageId + "/questions/" + questionId, modification)
                .andExpect(status().isOk()));
        JsonNode nouvelles = apres.at("/sections/0/questions/0/options");
        assertThat(nouvelles.size()).isEqualTo(3);
        assertThat(nouvelles.get(0).get("id").asLong()).isEqualTo(idMais);
        assertThat(nouvelles.get(0).get("valeur").asText()).isEqualTo("mais_2");
        assertThat(nouvelles.get(1).get("valeur").asText()).isEqualTo("mais");
        assertThat(nouvelles.get(2).get("libelle").asText()).isEqualTo("Sorgho");
        assertThat(nouvelles.get(2).get("valeur").asText()).isEqualTo("mil");

        // Renommer sans donner de valeur : la valeur reste.
        modification.put("options", List.of(
                Map.of("id", idMais, "libelle", "Maïs blanc local"),
                Map.of("id", idMais2, "libelle", "Maïs jaune")));
        JsonNode renomme = lire(putAvec(jeton, "/api/sondages/" + sondageId + "/questions/" + questionId, modification)
                .andExpect(status().isOk()));
        assertThat(renomme.at("/sections/0/questions/0/options/0/libelle").asText()).isEqualTo("Maïs blanc local");
        assertThat(renomme.at("/sections/0/questions/0/options/0/valeur").asText()).isEqualTo("mais_2");
    }

    @Test
    void changerLeTypeDUneQuestionRetireSesOptions() throws Exception {
        long sondageId = creerSondage("S");
        long sectionId = ajouterSection(sondageId, "Section");
        JsonNode sondage = ajouterQuestion(sondageId, sectionId, question("Q", "CHOIX_UNIQUE", null, "A", "B"));
        long questionId = sondage.at("/sections/0/questions/0/id").asLong();

        JsonNode apres = lire(putAvec(jeton, "/api/sondages/" + sondageId + "/questions/" + questionId,
                question("Q", "ECHELLE", Map.of("max", 5))).andExpect(status().isOk()));

        assertThat(apres.at("/sections/0/questions/0/type").asText()).isEqualTo("ECHELLE");
        assertThat(apres.at("/sections/0/questions/0/options").size()).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from option_question", Long.class)).isZero();
    }

    @Test
    void reordonnerLesSectionsEtLesQuestions() throws Exception {
        long sondageId = creerSondage("S");
        long s1 = ajouterSection(sondageId, "Un");
        long s2 = ajouterSection(sondageId, "Deux");
        long s3 = ajouterSection(sondageId, "Trois");

        JsonNode sondage = lire(putAvec(jeton, "/api/sondages/" + sondageId + "/sections/ordre",
                Map.of("ids", List.of(s3, s1, s2))).andExpect(status().isOk()));
        assertThat(titres(sondage.get("sections"))).containsExactly("Trois", "Un", "Deux");
        assertThat(sondage.at("/sections/0/ordre").asInt()).isEqualTo(1);

        for (List<Long> invalide : List.of(List.of(s1, s2), List.of(s1, s2, s3, 999L), List.of(s1, s1, s2))) {
            putAvec(jeton, "/api/sondages/" + sondageId + "/sections/ordre", Map.of("ids", invalide))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ORDRE_INVALIDE"));
        }

        ajouterQuestion(sondageId, s1, question("Q1", "OUI_NON", null));
        ajouterQuestion(sondageId, s1, question("Q2", "OUI_NON", null));
        JsonNode avant = ajouterQuestion(sondageId, s1, question("Q3", "OUI_NON", null));
        JsonNode questions = avant.at("/sections/1/questions");
        long q1 = questions.get(0).get("id").asLong();
        long q2 = questions.get(1).get("id").asLong();
        long q3 = questions.get(2).get("id").asLong();

        JsonNode apres = lire(putAvec(jeton, "/api/sondages/" + sondageId + "/sections/" + s1 + "/questions/ordre",
                Map.of("ids", List.of(q2, q3, q1))).andExpect(status().isOk()));
        assertThat(libelles(apres.at("/sections/1/questions"))).containsExactly("Q2", "Q3", "Q1");

        // Une question d'une autre section n'est pas acceptée.
        putAvec(jeton, "/api/sondages/" + sondageId + "/sections/" + s2 + "/questions/ordre", Map.of("ids", List.of(q1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ORDRE_INVALIDE"));
    }

    @Test
    void supprimerRenumeroteEtSupprimerLeSondageSupprimeTout() throws Exception {
        long sondageId = creerSondage("S");
        long s1 = ajouterSection(sondageId, "Un");
        long s2 = ajouterSection(sondageId, "Deux");
        ajouterQuestion(sondageId, s2, question("Q1", "OUI_NON", null));
        JsonNode sondage = ajouterQuestion(sondageId, s2, question("Q2", "CHOIX_UNIQUE", null, "A", "B"));
        long q1 = sondage.at("/sections/1/questions/0/id").asLong();

        JsonNode apres = lire(deleteAvec(jeton, "/api/sondages/" + sondageId + "/questions/" + q1)
                .andExpect(status().isOk()));
        assertThat(apres.at("/sections/1/questions/0/libelle").asText()).isEqualTo("Q2");
        assertThat(apres.at("/sections/1/questions/0/ordre").asInt()).isEqualTo(1);

        apres = lire(deleteAvec(jeton, "/api/sondages/" + sondageId + "/sections/" + s1).andExpect(status().isOk()));
        assertThat(apres.get("sections").size()).isEqualTo(1);
        assertThat(apres.at("/sections/0/ordre").asInt()).isEqualTo(1);

        deleteAvec(jeton, "/api/sondages/" + sondageId).andExpect(status().isNoContent());
        getAvec(jeton, "/api/sondages/" + sondageId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SONDAGE_INTROUVABLE"));
        for (String table : List.of("section", "question", "option_question")) {
            assertThat(jdbcTemplate.queryForObject("select count(*) from " + table, Long.class)).isZero();
        }
    }

    @Test
    void modifierLeSondageEtUneSection() throws Exception {
        long sondageId = creerSondage("Ancien titre");
        long sectionId = ajouterSection(sondageId, "Ancienne section");

        putAvec(jeton, "/api/sondages/" + sondageId, Map.of("titre", "Nouveau titre", "description", "Desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre").value("Nouveau titre"))
                .andExpect(jsonPath("$.description").value("Desc"));
        putAvec(jeton, "/api/sondages/" + sondageId + "/sections/" + sectionId, Map.of("titre", "Nouvelle section"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].titre").value("Nouvelle section"));
        postAvec(jeton, "/api/sondages", Map.of("titre", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"));
    }

    // ---------------------------------------------------------------- utilitaires

    static Map<String, Object> question(String libelle, String type, Map<String, Object> configuration,
                                       String... options) {
        Map<String, Object> question = new LinkedHashMap<>();
        question.put("libelle", libelle);
        question.put("type", type);
        question.put("obligatoire", true);
        if (configuration != null) {
            question.put("configuration", configuration);
        }
        List<Map<String, Object>> liste = new ArrayList<>();
        for (String option : options) {
            liste.add(Map.of("libelle", option));
        }
        question.put("options", liste);
        return question;
    }

    private long creerSondage(String titre) throws Exception {
        return lire(postAvec(jeton, "/api/sondages", Map.of("titre", titre)).andExpect(status().isCreated()))
                .get("id").asLong();
    }

    private long ajouterSection(long sondageId, String titre) throws Exception {
        JsonNode sondage = lire(postAvec(jeton, "/api/sondages/" + sondageId + "/sections", Map.of("titre", titre))
                .andExpect(status().isCreated()));
        JsonNode sections = sondage.get("sections");
        return sections.get(sections.size() - 1).get("id").asLong();
    }

    private JsonNode ajouterQuestion(long sondageId, long sectionId, Map<String, Object> question) throws Exception {
        return lire(postAvec(jeton, "/api/sondages/" + sondageId + "/sections/" + sectionId + "/questions", question)
                .andExpect(status().isCreated()));
    }

    private JsonNode lire(org.springframework.test.web.servlet.ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }

    private static List<String> titres(JsonNode sections) {
        List<String> titres = new ArrayList<>();
        sections.forEach(s -> titres.add(s.get("titre").asText()));
        return titres;
    }

    private static List<String> libelles(JsonNode questions) {
        List<String> libelles = new ArrayList<>();
        questions.forEach(q -> libelles.add(q.get("libelle").asText()));
        return libelles;
    }
}
