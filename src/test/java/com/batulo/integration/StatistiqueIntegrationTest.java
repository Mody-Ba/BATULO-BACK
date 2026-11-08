package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Étape 3.1 : statistiques de base, vérifiées sur un jeu de 6 réponses connu (les valeurs attendues se calculent à
 * la main, voir chaque assertion).
 */
class StatistiqueIntegrationTest extends PostgresIntegrationTestBase {

    private Utilisateur admin;
    private String jetonAdmin;
    private long sondageId;
    private Map<String, Long> questions;

    @BeforeEach
    void jeuDeSixReponses() throws Exception {
        admin = adminActif("awa@test.com");
        jetonAdmin = jeton("awa@test.com");
        sondageId = creerEtPublier("Enquête agricole", List.of(
                q("Région", "CHOIX_UNIQUE", true, null, "Kaolack", "Fatick"),
                q("Cultures", "CHOIX_MULTIPLE", false, null, "Arachide", "Mil", "Maïs"),
                q("Engrais ?", "OUI_NON", true, null),
                q("Satisfaction", "ECHELLE", true, Map.of("max", 5)),
                q("Surface (ha)", "NUMERIQUE", true, Map.of("min", 0, "max", 500, "decimales", 1)),
                q("Semis", "DATE", false, null),
                q("Priorités", "CLASSEMENT", true, null, "Semences", "Engrais", "Crédit"),
                q("Services", "MATRICE", true, Map.of("lignes", List.of("Vulgarisation", "Marché"),
                        "colonnes", List.of("Mauvais", "Moyen", "Bon"))),
                q("Difficultés", "TEXTE_LONG", false, null)));
        questions = ids(sondageId);

        Object[][] jeu = {
                // Région, Cultures, Engrais, Satisf., Surface, Semis, Priorités, Vulgarisation, Marché, Difficultés
                {"kaolack", List.of("arachide", "mil"), true, 4, 1.5, "2026-07-01",
                        List.of("engrais", "semences", "credit"), "Bon", "Moyen", "Eau"},
                {"kaolack", List.of("arachide"), true, 5, 2, "2026-07-01",
                        List.of("engrais", "credit", "semences"), "Bon", "Bon", null},
                {"fatick", List.of("mil", "mais"), false, 3, 3.5, "2026-07-15",
                        List.of("semences", "engrais", "credit"), "Moyen", "Moyen", "Engrais cher"},
                {"kaolack", List.of("arachide"), true, 4, 10, null,
                        List.of("engrais", "semences", "credit"), "Bon", "Moyen", null},
                {"kaolack", null, false, 2, 0.5, "2026-08-02",
                        List.of("credit", "engrais", "semences"), "Mauvais", "Mauvais", null},
                {"fatick", List.of("arachide", "mais"), true, 4, 2, null,
                        List.of("engrais", "semences", "credit"), "Bon", "Moyen", "Crédit"}};
        String[] libelles = {"Région", "Cultures", "Engrais ?", "Satisfaction", "Surface (ha)", "Semis", "Priorités"};
        String[] dates = {"2026-07-10T14:00:00Z", "2026-07-10T15:00:00Z", "2026-07-10T16:00:00Z",
                "2026-07-20T14:00:00Z", "2026-07-20T15:00:00Z", "2026-07-20T16:00:00Z"};
        for (int i = 0; i < jeu.length; i++) {
            Map<String, Object> reponse = new LinkedHashMap<>();
            for (int j = 0; j < libelles.length; j++) {
                reponse.put(libelles[j], jeu[i][j]);
            }
            reponse.put("Services", Map.of("Vulgarisation", jeu[i][7], "Marché", jeu[i][8]));
            reponse.put("Difficultés", jeu[i][9]);
            repondre(sondageId, questions, reponse);
            jdbcTemplate.update("update reponse set date_soumission = ? where id = (select max(id) from reponse)",
                    Timestamp.from(Instant.parse(dates[i])));
        }
    }

    @Test
    void chaqueCalculSurLeJeuConnu() throws Exception {
        JsonNode stats = statistiques("");
        assertThat(stats.at("/resume/nombreReponses").asLong()).isEqualTo(6);
        assertThat(stats.at("/resume/premiereReponse").asText()).isEqualTo("2026-07-10T14:00:00Z");
        assertThat(stats.at("/resume/derniereReponse").asText()).isEqualTo("2026-07-20T16:00:00Z");
        assertThat(stats.get("versions").toString()).isEqualTo("[1]");

        // Région : kaolack 4/6, fatick 2/6.
        JsonNode region = question(stats, "Région");
        assertThat(region.get("nombreRepondants").asLong()).isEqualTo(6);
        assertOption(region, 0, "kaolack", "Kaolack", 4, "66.7");
        assertOption(region, 1, "fatick", "Fatick", 2, "33.3");

        // Cultures (facultative, choix multiple) : 5 répondants, 1 sans réponse ; somme > 100 %.
        JsonNode cultures = question(stats, "Cultures");
        assertThat(cultures.get("nombreRepondants").asLong()).isEqualTo(5);
        assertThat(cultures.get("sansReponse").asLong()).isEqualTo(1);
        assertOption(cultures, 0, "arachide", "Arachide", 4, "80.0");
        assertOption(cultures, 1, "mil", "Mil", 2, "40.0");
        assertOption(cultures, 2, "mais", "Maïs", 2, "40.0");

        // Oui/Non : 4 oui, 2 non.
        JsonNode engrais = question(stats, "Engrais ?");
        assertOption(engrais, 0, "true", "Oui", 4, "66.7");
        assertOption(engrais, 1, "false", "Non", 2, "33.3");

        // Échelle : 4,5,3,4,2,4 → moyenne 22/6 = 3.67 ; triées 2,3,4,4,4,5 → médiane (4+4)/2 = 4.
        JsonNode satisfaction = question(stats, "Satisfaction").get("numerique");
        assertThat(satisfaction.get("moyenne").decimalValue()).isEqualByComparingTo("3.67");
        assertThat(satisfaction.get("mediane").decimalValue()).isEqualByComparingTo("4");
        assertThat(satisfaction.get("min").decimalValue()).isEqualByComparingTo("2");
        assertThat(satisfaction.get("max").decimalValue()).isEqualByComparingTo("5");
        JsonNode notes = satisfaction.get("distribution");
        assertThat(notes.size()).isEqualTo(5);
        assertClasse(notes.get(0), "1", 0, "0.0");
        assertClasse(notes.get(1), "2", 1, "16.7");
        assertClasse(notes.get(3), "4", 3, "50.0");
        assertClasse(notes.get(4), "5", 1, "16.7");

        // Numérique : 1.5, 2, 3.5, 10, 0.5, 2 → moyenne 19.5/6 = 3.25 ; triées 0.5,1.5,2,2,3.5,10 → médiane 2.
        JsonNode surface = question(stats, "Surface (ha)").get("numerique");
        assertThat(surface.get("moyenne").decimalValue()).isEqualByComparingTo("3.25");
        assertThat(surface.get("mediane").decimalValue()).isEqualByComparingTo("2");
        assertThat(surface.get("min").decimalValue()).isEqualByComparingTo("0.5");
        assertThat(surface.get("max").decimalValue()).isEqualByComparingTo("10");
        JsonNode valeurs = surface.get("distribution");
        assertThat(valeurs.size()).isEqualTo(5);
        assertClasse(valeurs.get(0), "0.5", 1, "16.7");
        assertClasse(valeurs.get(2), "2", 2, "33.3");
        assertClasse(valeurs.get(4), "10", 1, "16.7");

        // Date (facultative) : 4 répondants, écart de 32 jours → par jour.
        JsonNode semis = question(stats, "Semis");
        assertThat(semis.get("sansReponse").asLong()).isEqualTo(2);
        assertThat(semis.at("/dates/min").asText()).isEqualTo("2026-07-01");
        assertThat(semis.at("/dates/max").asText()).isEqualTo("2026-08-02");
        assertThat(semis.at("/dates/granularite").asText()).isEqualTo("JOUR");
        assertThat(semis.at("/dates/repartition").toString())
                .isEqualTo("[{\"periode\":\"2026-07-01\",\"nombre\":2},{\"periode\":\"2026-07-15\",\"nombre\":1},"
                        + "{\"periode\":\"2026-08-02\",\"nombre\":1}]");

        // Classement : engrais rangs 1,1,2,1,2,1 → 8/6 = 1.33 (4 fois 1er) ; semences 13/6 = 2.17 ; crédit 15/6 = 2.50.
        JsonNode priorites = question(stats, "Priorités").get("classement");
        assertRang(priorites.get(0), "engrais", "1.33", 4);
        assertRang(priorites.get(1), "semences", "2.17", 1);
        assertRang(priorites.get(2), "credit", "2.50", 1);

        // Matrice : Vulgarisation Bon 4, Moyen 1, Mauvais 1 ; Marché Moyen 4, Bon 1, Mauvais 1.
        JsonNode matrice = question(stats, "Services").get("matrice");
        assertThat(matrice.get("colonnes").toString()).isEqualTo("[\"Mauvais\",\"Moyen\",\"Bon\"]");
        assertCellule(matrice.at("/lignes/0"), "Vulgarisation", 2, "Bon", 4, "66.7");
        assertCellule(matrice.at("/lignes/0"), "Vulgarisation", 0, "Mauvais", 1, "16.7");
        assertCellule(matrice.at("/lignes/1"), "Marché", 1, "Moyen", 4, "66.7");

        // Textes : 3 répondants, les plus récents d'abord.
        JsonNode difficultes = question(stats, "Difficultés");
        assertThat(difficultes.get("sansReponse").asLong()).isEqualTo(3);
        assertThat(difficultes.at("/textes/totalElements").asLong()).isEqualTo(3);
        assertThat(textes(difficultes.at("/textes/contenu"))).containsExactly("Crédit", "Engrais cher", "Eau");

        // Chaque question ne porte que le bloc de son type.
        assertThat(region.has("numerique") || region.has("matrice") || region.has("textes")).isFalse();
    }

    @Test
    void granulariteParMoisEtFiltreDePeriode() throws Exception {
        JsonNode parMois = statistiques("granulariteDate=MOIS");
        assertThat(question(parMois, "Semis").at("/dates/repartition").toString())
                .isEqualTo("[{\"periode\":\"2026-07\",\"nombre\":3},{\"periode\":\"2026-08\",\"nombre\":1}]");

        // Du 15 juillet : les 3 dernières réponses. Satisfaction 4,2,4 → moyenne 3.33, médiane (impair) 4.
        JsonNode periode = statistiques("du=2026-07-15");
        assertThat(periode.at("/resume/nombreReponses").asLong()).isEqualTo(3);
        assertOption(question(periode, "Région"), 0, "kaolack", "Kaolack", 2, "66.7");
        assertThat(question(periode, "Satisfaction").at("/numerique/moyenne").decimalValue()).isEqualByComparingTo("3.33");
        assertThat(question(periode, "Satisfaction").at("/numerique/mediane").decimalValue()).isEqualByComparingTo("4");

        assertThat(statistiques("au=2026-07-10").at("/resume/nombreReponses").asLong()).isEqualTo(3);
        assertThat(statistiques("du=2026-07-11&au=2026-07-19").at("/resume/nombreReponses").asLong()).isZero();
        getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/statistiques?du=2026-08-01&au=2026-07-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERIODE_INVALIDE"));
    }

    @Test
    void textesPagines() throws Exception {
        String url = "/api/sondages/" + sondageId + "/statistiques/questions/" + questions.get("Difficultés") + "/textes";
        JsonNode page0 = lire(getAvec(jetonAdmin, url + "?taille=2").andExpect(status().isOk()));
        assertThat(textes(page0.get("contenu"))).containsExactly("Crédit", "Engrais cher");
        assertThat(page0.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(textes(lire(getAvec(jetonAdmin, url + "?taille=2&page=1")).get("contenu"))).containsExactly("Eau");

        getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/statistiques/questions/" + questions.get("Région") + "/textes")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QUESTION_NON_TEXTE"));
        getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/statistiques/questions/999999/textes")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("QUESTION_INTROUVABLE"));
    }

    @Test
    void numeriqueEnTranchesAuDelaDeVingtValeurs() throws Exception {
        long id = creerEtPublier("Tranches", List.of(q("Âge", "NUMERIQUE", true, Map.of("min", 0, "max", 100))));
        Map<String, Long> ids = ids(id);
        for (int valeur = 1; valeur <= 25; valeur++) {
            repondre(id, ids, Map.of("Âge", valeur));
        }
        // 25 valeurs distinctes de 1 à 25 → 10 tranches de largeur 2.4 : [1 ; 3.4[ contient 1, 2, 3.
        JsonNode age = question(lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques")), "Âge");
        JsonNode tranches = age.at("/numerique/distribution");
        assertThat(tranches.size()).isEqualTo(10);
        assertThat(tranches.get(0).get("de").decimalValue()).isEqualByComparingTo("1");
        assertThat(tranches.get(0).get("a").decimalValue()).isEqualByComparingTo("3.4");
        assertThat(tranches.get(0).get("nombre").asLong()).isEqualTo(3);
        assertThat(tranches.get(9).get("a").decimalValue()).isEqualByComparingTo("25");
        assertThat(tranches.get(9).get("nombre").asLong()).isEqualTo(3);
        long total = 0;
        for (JsonNode tranche : tranches) {
            total += tranche.get("nombre").asLong();
            assertThat(tranche.has("valeur")).isFalse();
        }
        assertThat(total).isEqualTo(25);
        assertThat(age.at("/numerique/mediane").decimalValue()).isEqualByComparingTo("13");
    }

    @Test
    void toutesLesVersionsRegroupeesParCleDeQuestion() throws Exception {
        long v1 = creerEtPublier("Céréales", List.of(q("Céréale", "CHOIX_UNIQUE", true, null, "Mil", "Maïs")));
        Map<String, Long> idsV1 = ids(v1);
        repondre(v1, idsV1, Map.of("Céréale", "mil"));
        repondre(v1, idsV1, Map.of("Céréale", "mais"));

        // v2 : « Mil » renommé (même valeur), « Maïs » retiré, « Sorgho » ajouté, nouvelle question.
        JsonNode v2 = lire(postAvec(jetonAdmin, "/api/sondages/" + v1 + "/nouvelle-version", Map.of()));
        long idV2 = v2.get("id").asLong();
        JsonNode cereale = v2.at("/sections/0/questions/0");
        Map<String, Object> modifiee = q("Céréale", "CHOIX_UNIQUE", true, null);
        modifiee.put("options", List.of(Map.of("id", cereale.at("/options/0/id").asLong(), "libelle", "Mil local"),
                Map.of("libelle", "Sorgho")));
        putAvec(jetonAdmin, "/api/sondages/" + idV2 + "/questions/" + cereale.get("id").asLong(), modifiee)
                .andExpect(status().isOk());
        postAvec(jetonAdmin, "/api/sondages/" + idV2 + "/sections/" + v2.at("/sections/0/id").asLong() + "/questions",
                q("Nouvelle", "OUI_NON", true, null)).andExpect(status().isCreated());
        postAvec(jetonAdmin, "/api/sondages/" + idV2 + "/publier", Map.of()).andExpect(status().isOk());
        Map<String, Long> idsV2 = ids(idV2);
        repondre(idV2, idsV2, Map.of("Céréale", "mil", "Nouvelle", true));
        repondre(idV2, idsV2, Map.of("Céréale", "sorgho", "Nouvelle", false));
        repondre(idV2, idsV2, Map.of("Céréale", "mil", "Nouvelle", true));

        // Toute la lignée : mil 1 + 2 = 3 sur 5, avec le libellé le plus récent ; « Maïs » retiré reste compté.
        JsonNode lignee = lire(getAvec(jetonAdmin, "/api/sondages/" + v1 + "/statistiques?lignee=true")
                .andExpect(status().isOk()));
        assertThat(lignee.get("versions").toString()).isEqualTo("[1,2]");
        assertThat(lignee.at("/resume/nombreReponses").asLong()).isEqualTo(5);
        JsonNode stat = question(lignee, "Céréale");
        assertThat(stat.get("nombreRepondants").asLong()).isEqualTo(5);
        assertOption(stat, 0, "mil", "Mil local", 3, "60.0");
        assertOption(stat, 1, "sorgho", "Sorgho", 1, "20.0");
        assertOption(stat, 2, "mais", "Maïs", 1, "20.0");
        // La question ajoutée en v2 ne compte que les réponses à la v2.
        JsonNode nouvelle = question(lignee, "Nouvelle");
        assertThat(nouvelle.get("nombreRepondants").asLong()).isEqualTo(3);
        assertThat(nouvelle.get("sansReponse").asLong()).isZero();

        // Cette version seulement.
        JsonNode version2 = lire(getAvec(jetonAdmin, "/api/sondages/" + idV2 + "/statistiques"));
        assertThat(version2.at("/resume/nombreReponses").asLong()).isEqualTo(3);
        assertThat(question(version2, "Céréale").get("options").size()).isEqualTo(2);
        assertOption(question(version2, "Céréale"), 0, "mil", "Mil local", 2, "66.7");
    }

    @Test
    void sondageSansReponse() throws Exception {
        long id = creerEtPublier("Vide", List.of(q("Région", "CHOIX_UNIQUE", true, null, "A", "B"),
                q("Note", "ECHELLE", true, Map.of("max", 5))));
        JsonNode stats = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques").andExpect(status().isOk()));
        assertThat(stats.at("/resume/nombreReponses").asLong()).isZero();
        assertOption(question(stats, "Région"), 0, "a", "A", 0, "0.0");
        assertThat(question(stats, "Note").at("/numerique/moyenne").isMissingNode()).isTrue();
    }

    @Test
    void droitsEtIsolation() throws Exception {
        Utilisateur assigne = gestionnaire("g1@test.com", admin, false);
        gestionnaire("g2@test.com", admin, true);
        postAvec(jetonAdmin, "/api/sondages/" + sondageId + "/assignations",
                Map.of("gestionnaireIds", List.of(assigne.getId()))).andExpect(status().isOk());
        String url = "/api/sondages/" + sondageId + "/statistiques";

        getAvec(jeton("g1@test.com"), url).andExpect(status().isOk()).andExpect(jsonPath("$.resume.nombreReponses").value(6));
        getAvec(jeton("g2@test.com"), url).andExpect(status().isNotFound());
        adminActif("b@test.com");
        getAvec(jeton("b@test.com"), url).andExpect(status().isNotFound());
        superAdmin("proprio@test.com");
        getAvec(jeton("proprio@test.com"), url).andExpect(status().isForbidden());

        // Lecture seule : permis avec un abonnement expiré.
        jdbcTemplate.update("update abonnement set date_expiration = ? where entreprise_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1))), admin.getEntreprise().getId());
        getAvec(jetonAdmin, url).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- utilitaires

    static Map<String, Object> q(String libelle, String type, boolean obligatoire, Map<String, Object> configuration,
                                 String... options) {
        Map<String, Object> question = SondageIntegrationTest.question(libelle, type, configuration, options);
        question.put("obligatoire", obligatoire);
        return question;
    }

    private long creerEtPublier(String titre, List<Map<String, Object>> liste) throws Exception {
        long id = lire(postAvec(jetonAdmin, "/api/sondages", Map.of("titre", titre, "reponseUnique", false))
                .andExpect(status().isCreated())).get("id").asLong();
        long section = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/sections", Map.of("titre", "Section")))
                .at("/sections/0/id").asLong();
        for (Map<String, Object> question : liste) {
            postAvec(jetonAdmin, "/api/sondages/" + id + "/sections/" + section + "/questions", question)
                    .andExpect(status().isCreated());
        }
        postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
        return id;
    }

    private Map<String, Long> ids(long id) throws Exception {
        Map<String, Long> ids = new HashMap<>();
        lire(getAvec(jetonAdmin, "/api/sondages/" + id)).at("/sections/0/questions")
                .forEach(q -> ids.put(q.get("libelle").asText(), q.get("id").asLong()));
        return ids;
    }

    private void repondre(long id, Map<String, Long> ids, Map<String, Object> parLibelle) throws Exception {
        String jeton = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/lien")).get("jeton").asText();
        List<Map<String, Object>> reponses = new ArrayList<>();
        parLibelle.forEach((libelle, valeur) -> {
            if (valeur != null) {
                reponses.add(Map.of("questionId", ids.get(libelle), "valeur", valeur));
            }
        });
        mockMvc.perform(post("/api/public/sondages/" + jeton + "/reponses").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reponses", reponses)))).andExpect(status().isCreated());
    }

    private JsonNode statistiques(String parametres) throws Exception {
        return lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/statistiques?" + parametres)
                .andExpect(status().isOk()));
    }

    private static JsonNode question(JsonNode stats, String libelle) {
        for (JsonNode question : stats.get("questions")) {
            if (question.get("libelle").asText().equals(libelle)) {
                return question;
            }
        }
        throw new AssertionError("Question absente : " + libelle);
    }

    private static void assertOption(JsonNode question, int index, String valeur, String libelle, long nombre,
                                     String pourcentage) {
        JsonNode option = question.get("options").get(index);
        assertThat(option.get("valeur").asText()).isEqualTo(valeur);
        assertThat(option.get("libelle").asText()).isEqualTo(libelle);
        assertThat(option.get("nombre").asLong()).isEqualTo(nombre);
        assertThat(option.get("pourcentage").decimalValue()).isEqualByComparingTo(pourcentage);
    }

    private static void assertClasse(JsonNode classe, String valeur, long nombre, String pourcentage) {
        assertThat(classe.get("valeur").decimalValue()).isEqualByComparingTo(valeur);
        assertThat(classe.get("nombre").asLong()).isEqualTo(nombre);
        assertThat(classe.get("pourcentage").decimalValue()).isEqualByComparingTo(pourcentage);
    }

    private static void assertRang(JsonNode rang, String valeur, String rangMoyen, long premier) {
        assertThat(rang.get("valeur").asText()).isEqualTo(valeur);
        assertThat(rang.get("rangMoyen").decimalValue()).isEqualByComparingTo(rangMoyen);
        assertThat(rang.get("nombrePremier").asLong()).isEqualTo(premier);
    }

    private static void assertCellule(JsonNode ligne, String nom, int index, String colonne, long nombre,
                                      String pourcentage) {
        assertThat(ligne.get("ligne").asText()).isEqualTo(nom);
        assertThat(ligne.get("nombre").asLong()).isEqualTo(6);
        JsonNode cellule = ligne.get("cellules").get(index);
        assertThat(cellule.get("colonne").asText()).isEqualTo(colonne);
        assertThat(cellule.get("nombre").asLong()).isEqualTo(nombre);
        assertThat(cellule.get("pourcentage").decimalValue()).isEqualByComparingTo(pourcentage);
    }

    private static List<String> textes(JsonNode contenu) {
        List<String> textes = new ArrayList<>();
        contenu.forEach(t -> textes.add(t.get("texte").asText()));
        return textes;
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
