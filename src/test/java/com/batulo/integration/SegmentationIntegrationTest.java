package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Étape 3.2 : critères d'analyse, statistiques filtrées et tableaux croisés, vérifiés sur un jeu de 12 réponses.
 * Âge en tranches [18, 30, 45] : t1 < 18, t2 [18 ; 30[, t3 [30 ; 45[, t4 ≥ 45.
 */
class SegmentationIntegrationTest extends PostgresIntegrationTestBase {

    private Utilisateur admin;
    private String jetonAdmin;
    private long sondageId;
    private Map<String, Long> ids;
    private Map<String, String> cles;

    @BeforeEach
    void jeuDeDouzeReponses() throws Exception {
        admin = adminActif("awa@test.com");
        jetonAdmin = jeton("awa@test.com");
        sondageId = creerEtPublier(List.of(
                StatistiqueIntegrationTest.q("Région", "CHOIX_UNIQUE", true, null, "Kayes", "Kaolack"),
                StatistiqueIntegrationTest.q("Sexe", "CHOIX_UNIQUE", false, null, "Femme", "Homme"),
                StatistiqueIntegrationTest.q("Âge", "NUMERIQUE", true, Map.of("min", 0, "max", 100)),
                StatistiqueIntegrationTest.q("Date", "DATE", false, null),
                StatistiqueIntegrationTest.q("Cultures", "CHOIX_MULTIPLE", true, null, "Mil", "Arachide"),
                StatistiqueIntegrationTest.q("Poids", "NUMERIQUE", true, Map.of("min", 0, "max", 200, "decimales", 1)),
                StatistiqueIntegrationTest.q("Satisfaction", "ECHELLE", true, Map.of("max", 5)),
                StatistiqueIntegrationTest.q("Engrais", "OUI_NON", true, null)));
        ids = idsQuestions(sondageId);

        // Les critères se marquent APRÈS la publication (réglages d'analyse).
        critere("Région", true, null).andExpect(status().isOk());
        critere("Sexe", true, null).andExpect(status().isOk());
        critere("Âge", true, Map.of("tranches", List.of(18, 30, 45))).andExpect(status().isOk());
        critere("Date", true, Map.of("granularite", "MOIS")).andExpect(status().isOk());

        Object[][] jeu = {
                {"kayes", "femme", 25, "2026-06-10", List.of("mil"), 60, 4, true},
                {"kayes", "femme", 40, "2026-06-12", List.of("mil", "arachide"), 70, 5, true},
                {"kayes", "femme", 17, "2026-07-01", List.of("arachide"), 50, 3, false},
                {"kayes", "homme", 50, "2026-07-02", List.of("mil"), 80, 4, true},
                {"kayes", "homme", 33, "2026-07-05", List.of("mil"), 75, 2, false},
                {"kayes", null, 29, null, List.of("mil", "arachide"), 65, 4, true},
                {"kaolack", "femme", 22, "2026-06-20", List.of("arachide"), 55, 3, false},
                {"kaolack", "femme", 45, "2026-07-10", List.of("arachide"), 62, 5, true},
                {"kaolack", "homme", 60, "2026-07-11", List.of("mil"), 90, 2, false},
                {"kaolack", "homme", 18, "2026-07-12", List.of("arachide"), 58, 3, true},
                {"kaolack", "homme", 30, "2026-07-15", List.of("mil", "arachide"), 72, 4, true},
                {"kaolack", null, 44, null, List.of("mil"), 68, 1, false}};
        String[] libelles = {"Région", "Sexe", "Âge", "Date", "Cultures", "Poids", "Satisfaction", "Engrais"};
        for (Object[] ligne : jeu) {
            Map<String, Object> reponse = new LinkedHashMap<>();
            for (int i = 0; i < libelles.length; i++) {
                reponse.put(libelles[i], ligne[i]);
            }
            repondre(sondageId, ids, reponse);
        }
        cles = new HashMap<>();
        lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/statistiques")).get("questions")
                .forEach(q -> cles.put(q.get("libelle").asText(), q.get("cle").asText()));
    }

    @Test
    void criteresEtLeursValeurs() throws Exception {
        JsonNode criteres = lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/statistiques/criteres")
                .andExpect(status().isOk()));
        assertThat(criteres.size()).isEqualTo(4);
        assertThat(valeurs(critereNomme(criteres, "Région"), "valeur")).containsExactly("kayes", "kaolack");
        assertThat(valeurs(critereNomme(criteres, "Âge"), "libelle"))
                .containsExactly("Moins de 18", "18 à moins de 30", "30 à moins de 45", "45 et plus");
        assertThat(valeurs(critereNomme(criteres, "Âge"), "valeur")).containsExactly("t1", "t2", "t3", "t4");
        assertThat(valeurs(critereNomme(criteres, "Date"), "valeur")).containsExactly("2026-06", "2026-07");

        // Le répondant ne voit jamais ce réglage d'analyse.
        String jeton = lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/lien")).get("jeton").asText();
        JsonNode formulaire = lire(mockMvc.perform(get("/api/public/sondages/" + jeton)));
        assertThat(formulaire.at("/sections/0/questions/0").has("estCritere")).isFalse();
        assertThat(formulaire.at("/sections/0/questions/0").has("critereConfig")).isFalse();
        // L'admin le voit dans le sondage.
        assertThat(lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId)).at("/sections/0/questions/0/estCritere")
                .asBoolean()).isTrue();
    }

    @Test
    void statistiquesFiltreesEtOuOu() throws Exception {
        // Région = kayes ET sexe = femme : réponses 1, 2, 3.
        JsonNode filtrees = stats("critere=" + cles.get("Région") + ":kayes&critere=" + cles.get("Sexe") + ":femme");
        assertThat(filtrees.at("/resume/nombreReponses").asLong()).isEqualTo(3);
        assertThat(filtrees.at("/resume/petitEffectif").asBoolean()).isTrue();
        assertThat(filtrees.get("criteres").size()).isEqualTo(2);
        assertThat(filtrees.at("/criteres/0/valeurs/0/libelle").asText()).isEqualTo("Kayes");
        JsonNode cultures = question(filtrees, "Cultures");
        assertThat(cultures.get("nombreRepondants").asLong()).isEqualTo(3);
        assertThat(cultures.get("petitEffectif").asBoolean()).isTrue();
        assertThat(cultures.at("/options/0/nombre").asLong()).isEqualTo(2);
        assertThat(cultures.at("/options/0/pourcentage").decimalValue()).isEqualByComparingTo("66.7");
        assertThat(question(filtrees, "Poids").at("/numerique/moyenne").decimalValue()).isEqualByComparingTo("60");

        // OU dans une même clé : femme OU homme écarte les 2 sans réponse au sexe.
        assertThat(stats("critere=" + cles.get("Sexe") + ":femme&critere=" + cles.get("Sexe") + ":homme")
                .at("/resume/nombreReponses").asLong()).isEqualTo(10);
        // Tranche d'âge t4 (45 et plus) : réponses 4, 8, 9 ; mois 2026-07 : 7 réponses.
        assertThat(stats("critere=" + cles.get("Âge") + ":t4").at("/resume/nombreReponses").asLong()).isEqualTo(3);
        assertThat(stats("critere=" + cles.get("Date") + ":2026-07").at("/resume/nombreReponses").asLong()).isEqualTo(7);
        // Sans filtre : tous, et pas de petit effectif.
        JsonNode tous = stats("");
        assertThat(tous.at("/resume/nombreReponses").asLong()).isEqualTo(12);
        assertThat(tous.at("/resume/petitEffectif").asBoolean()).isFalse();
        assertThat(tous.get("criteres").size()).isZero();
    }

    @Test
    void croisementQuestionAChoixMultipleParRegion() throws Exception {
        JsonNode tableau = croisement("Cultures", "Région", "");
        assertThat(tableau.at("/question/libelle").asText()).isEqualTo("Cultures");
        assertThat(tableau.at("/par/libelle").asText()).isEqualTo("Région");
        assertThat(tableau.get("colonnes").size()).isEqualTo(2);
        assertThat(tableau.at("/colonnes/0/nombreRepondants").asLong()).isEqualTo(6);
        assertThat(tableau.at("/colonnes/0/petitEffectif").asBoolean()).isFalse();
        // Mil : Kayes 5 (62.5 % de la ligne, 5/6 = 83.3 % de la colonne), Kaolack 3 (37.5 %, 50 %).
        assertCellule(tableau, "mil", 0, 5, "62.5", "83.3");
        assertCellule(tableau, "mil", 1, 3, "37.5", "50.0");
        // Arachide : Kayes 3 (42.9 %, 50 %), Kaolack 4 (57.1 %, 66.7 %).
        assertCellule(tableau, "arachide", 0, 3, "42.9", "50.0");
        assertCellule(tableau, "arachide", 1, 4, "57.1", "66.7");
        assertThat(tableau.has("numerique")).isFalse();

        // Avec un filtre : femmes seulement (Kayes 1, 2, 3 ; Kaolack 7, 8).
        JsonNode femmes = croisement("Cultures", "Région", "&critere=" + cles.get("Sexe") + ":femme");
        assertThat(femmes.at("/colonnes/0/nombreRepondants").asLong()).isEqualTo(3);
        assertThat(femmes.at("/colonnes/1/nombreRepondants").asLong()).isEqualTo(2);
        assertThat(femmes.at("/colonnes/1/petitEffectif").asBoolean()).isTrue();
    }

    @Test
    void croisementNumeriqueMoyenneParColonne() throws Exception {
        // Poids : Kayes 50,60,65,70,75,80 → moyenne 66.67, médiane 67.5 ; Kaolack 55,58,62,68,72,90 → 67.50, 65.
        JsonNode tableau = croisement("Poids", "Région", "");
        assertThat(tableau.has("lignes")).isFalse();
        JsonNode kayes = tableau.at("/numerique/0");
        assertThat(kayes.get("colonne").asText()).isEqualTo("kayes");
        assertThat(kayes.get("nombre").asLong()).isEqualTo(6);
        assertThat(kayes.get("moyenne").decimalValue()).isEqualByComparingTo("66.67");
        assertThat(kayes.get("mediane").decimalValue()).isEqualByComparingTo("67.5");
        assertThat(kayes.get("min").decimalValue()).isEqualByComparingTo("50");
        assertThat(kayes.get("max").decimalValue()).isEqualByComparingTo("80");
        JsonNode kaolack = tableau.at("/numerique/1");
        assertThat(kaolack.get("moyenne").decimalValue()).isEqualByComparingTo("67.50");
        assertThat(kaolack.get("mediane").decimalValue()).isEqualByComparingTo("65");
    }

    @Test
    void croisementAvecNonRenseigneEtTranches() throws Exception {
        // Engrais par sexe : colonne « Non renseigné » (réponses 6 et 12).
        JsonNode parSexe = croisement("Engrais", "Sexe", "");
        assertThat(parSexe.get("colonnes").size()).isEqualTo(3);
        JsonNode nonRenseigne = parSexe.at("/colonnes/2");
        assertThat(nonRenseigne.get("valeur").isNull()).isTrue();
        assertThat(nonRenseigne.get("libelle").asText()).isEqualTo("Non renseigné");
        assertThat(nonRenseigne.get("nombreRepondants").asLong()).isEqualTo(2);
        assertThat(nonRenseigne.get("petitEffectif").asBoolean()).isTrue();
        // Oui : femmes 1, 2, 8 (3 sur 5 = 60 % ; 3 sur 7 oui = 42.9 %), non renseigné 1.
        assertCellule(parSexe, "true", 0, 3, "42.9", "60.0");
        assertCellule(parSexe, "true", 2, 1, "14.3", "50.0");

        // Satisfaction (échelle) par tranche d'âge : notes en lignes et moyenne par colonne.
        JsonNode parAge = croisement("Satisfaction", "Âge", "");
        assertThat(parAge.get("colonnes").size()).isEqualTo(4);
        assertThat(parAge.at("/colonnes/1/libelle").asText()).isEqualTo("18 à moins de 30");
        assertThat(parAge.at("/colonnes/1/nombreRepondants").asLong()).isEqualTo(4);
        assertThat(parAge.get("lignes").size()).isEqualTo(5);
        // Tranche 18-30 : réponses 1, 6, 7, 10 → 4, 4, 3, 3 → moyenne 3.50.
        assertThat(parAge.at("/numerique/1/moyenne").decimalValue()).isEqualByComparingTo("3.50");
        assertCellule(parAge, "4", 1, 2, "50.0", "50.0");
    }

    @Test
    void erreursDeCriteresEtDeCroisement() throws Exception {
        String base = "/api/sondages/" + sondageId + "/statistiques";
        getAvec(jetonAdmin, base + "?critere=" + cles.get("Cultures") + ":mil")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CRITERE_INVALIDE"));
        getAvec(jetonAdmin, base + "?critere=" + cles.get("Région") + ":dakar")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CRITERE_INVALIDE"));
        getAvec(jetonAdmin, base + "?critere=sans-separateur")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CRITERE_INVALIDE"));
        getAvec(jetonAdmin, base + "?critere=" + cles.get("Date") + ":2026")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CRITERE_INVALIDE"));

        String croisement = base + "/croisement?question=";
        getAvec(jetonAdmin, croisement + cles.get("Région") + "&par=" + cles.get("Région"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CROISEMENT_INVALIDE"));
        getAvec(jetonAdmin, croisement + cles.get("Date") + "&par=" + cles.get("Région"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CROISEMENT_INVALIDE"));
        getAvec(jetonAdmin, croisement + cles.get("Cultures") + "&par=" + cles.get("Poids"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CRITERE_INVALIDE"));
        getAvec(jetonAdmin, croisement + "inconnue&par=" + cles.get("Région"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("QUESTION_INTROUVABLE"));
    }

    @Test
    void reglagesDuCritere() throws Exception {
        // Types non éligibles, tranches invalides, NUMERIQUE sans bornes ni tranches.
        critere("Cultures", true, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("QUESTION_INVALIDE"));
        critere("Âge", true, Map.of("tranches", List.of(30, 18))).andExpect(status().isBadRequest());
        critere("Région", true, Map.of("tranches", List.of(1))).andExpect(status().isBadRequest());
        critere("Date", true, Map.of("granularite", "SEMAINE")).andExpect(status().isBadRequest());

        // Tranches par défaut : 5 tranches égales entre min (0) et max (100).
        critere("Âge", true, null).andExpect(status().isOk());
        JsonNode age = critereNomme(lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/statistiques/criteres")), "Âge");
        assertThat(valeurs(age, "libelle")).containsExactly("Moins de 20", "20 à moins de 40", "40 à moins de 60",
                "60 à moins de 80", "80 et plus");

        // Granularité ANNEE : le filtre se fait par année.
        critere("Date", true, Map.of("granularite", "ANNEE")).andExpect(status().isOk());
        assertThat(stats("critere=" + cles.get("Date") + ":2026").at("/resume/nombreReponses").asLong()).isEqualTo(10);

        // Retirer un critère, puis le recopier dans une nouvelle version.
        critere("Sexe", false, null).andExpect(status().isOk());
        getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/statistiques?critere=" + cles.get("Sexe") + ":femme")
                .andExpect(status().isBadRequest());
        JsonNode v2 = lire(postAvec(jetonAdmin, "/api/sondages/" + sondageId + "/nouvelle-version", Map.of()));
        JsonNode questionAge = v2.at("/sections/0/questions/2");
        assertThat(questionAge.get("estCritere").asBoolean()).isTrue();
        assertThat(v2.at("/sections/0/questions/1/estCritere").asBoolean()).isFalse();
        assertThat(v2.at("/sections/0/questions/3/critereConfig/granularite").asText()).isEqualTo("ANNEE");
    }

    @Test
    void droitsEtLignee() throws Exception {
        gestionnaire("g@test.com", admin, true);
        getAvec(jeton("g@test.com"), "/api/sondages/" + sondageId + "/statistiques/criteres").andExpect(status().isNotFound());
        getAvec(jeton("g@test.com"), "/api/sondages/" + sondageId + "/statistiques/croisement?question="
                + cles.get("Cultures") + "&par=" + cles.get("Région")).andExpect(status().isNotFound());

        // v2 publiée avec les mêmes critères : le filtre porte sur toute la lignée.
        long v2 = lire(postAvec(jetonAdmin, "/api/sondages/" + sondageId + "/nouvelle-version", Map.of())).get("id").asLong();
        postAvec(jetonAdmin, "/api/sondages/" + v2 + "/publier", Map.of()).andExpect(status().isOk());
        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("Région", "kayes");
        reponse.put("Âge", 20);
        reponse.put("Cultures", List.of("mil"));
        reponse.put("Poids", 61);
        reponse.put("Satisfaction", 3);
        reponse.put("Engrais", true);
        repondre(v2, idsQuestions(v2), reponse);
        JsonNode lignee = lire(getAvec(jetonAdmin, "/api/sondages/" + v2 + "/statistiques?lignee=true&critere="
                + cles.get("Région") + ":kayes").andExpect(status().isOk()));
        assertThat(lignee.at("/resume/nombreReponses").asLong()).isEqualTo(7);
        JsonNode version = lire(getAvec(jetonAdmin, "/api/sondages/" + v2 + "/statistiques?critere="
                + cles.get("Région") + ":kayes"));
        assertThat(version.at("/resume/nombreReponses").asLong()).isEqualTo(1);
    }

    // ---------------------------------------------------------------- utilitaires

    private ResultActions critere(String libelle, boolean estCritere, Map<String, Object> config) throws Exception {
        Map<String, Object> corps = new HashMap<>();
        corps.put("estCritere", estCritere);
        if (config != null) {
            corps.put("critereConfig", config);
        }
        return putAvec(jetonAdmin, "/api/sondages/" + sondageId + "/questions/" + ids.get(libelle) + "/critere", corps);
    }

    private JsonNode stats(String parametres) throws Exception {
        return lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/statistiques?" + parametres)
                .andExpect(status().isOk()));
    }

    private JsonNode croisement(String question, String par, String suite) throws Exception {
        return lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/statistiques/croisement?question="
                + cles.get(question) + "&par=" + cles.get(par) + suite).andExpect(status().isOk()));
    }

    private static void assertCellule(JsonNode tableau, String ligne, int colonne, long nombre, String pctLigne,
                                      String pctColonne) {
        for (JsonNode l : tableau.get("lignes")) {
            if (l.get("valeur").asText().equals(ligne)) {
                JsonNode cellule = l.get("cellules").get(colonne);
                assertThat(cellule.get("nombre").asLong()).as(ligne + "/" + colonne).isEqualTo(nombre);
                assertThat(cellule.get("pourcentageLigne").decimalValue()).isEqualByComparingTo(pctLigne);
                assertThat(cellule.get("pourcentageColonne").decimalValue()).isEqualByComparingTo(pctColonne);
                return;
            }
        }
        throw new AssertionError("Ligne absente : " + ligne);
    }

    private static JsonNode critereNomme(JsonNode criteres, String libelle) {
        for (JsonNode critere : criteres) {
            if (critere.get("libelle").asText().equals(libelle)) {
                return critere;
            }
        }
        throw new AssertionError("Critère absent : " + libelle);
    }

    private static List<String> valeurs(JsonNode critere, String champ) {
        List<String> valeurs = new ArrayList<>();
        critere.get("valeurs").forEach(v -> valeurs.add(v.get(champ).asText()));
        return valeurs;
    }

    private static JsonNode question(JsonNode stats, String libelle) {
        for (JsonNode question : stats.get("questions")) {
            if (question.get("libelle").asText().equals(libelle)) {
                return question;
            }
        }
        throw new AssertionError("Question absente : " + libelle);
    }

    private long creerEtPublier(List<Map<String, Object>> questions) throws Exception {
        long id = lire(postAvec(jetonAdmin, "/api/sondages", Map.of("titre", "Enquête", "reponseUnique", false)))
                .get("id").asLong();
        long section = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/sections", Map.of("titre", "S")))
                .at("/sections/0/id").asLong();
        for (Map<String, Object> question : questions) {
            postAvec(jetonAdmin, "/api/sondages/" + id + "/sections/" + section + "/questions", question)
                    .andExpect(status().isCreated());
        }
        postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
        return id;
    }

    private Map<String, Long> idsQuestions(long id) throws Exception {
        Map<String, Long> resultat = new HashMap<>();
        lire(getAvec(jetonAdmin, "/api/sondages/" + id)).at("/sections/0/questions")
                .forEach(q -> resultat.put(q.get("libelle").asText(), q.get("id").asLong()));
        return resultat;
    }

    private void repondre(long id, Map<String, Long> questions, Map<String, Object> parLibelle) throws Exception {
        String jeton = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/lien")).get("jeton").asText();
        List<Map<String, Object>> reponses = new ArrayList<>();
        parLibelle.forEach((libelle, valeur) -> {
            if (valeur != null) {
                reponses.add(Map.of("questionId", questions.get(libelle), "valeur", valeur));
            }
        });
        mockMvc.perform(post("/api/public/sondages/" + jeton + "/reponses").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reponses", reponses)))).andExpect(status().isCreated());
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
