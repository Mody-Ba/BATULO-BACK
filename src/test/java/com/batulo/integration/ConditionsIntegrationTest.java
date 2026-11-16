package com.batulo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4, étape 1 : questions conditionnelles. Formulaire de test :
 * <pre>
 * Section « Famille » : Enfants ? (OUI_NON) ; Combien ? (NUMERIQUE, si Enfants = Oui) ; Région (CHOIX_UNIQUE)
 * Section « Dakar »   (si Région = dakar) : Quartier (TEXTE_COURT)
 * </pre>
 * Toutes les questions sont obligatoires.
 */
class ConditionsIntegrationTest extends PostgresIntegrationTestBase {

    private String jetonAdmin;
    private long sondageId;
    private long famille;
    private long dakar;
    private Map<String, Long> questions;

    @BeforeEach
    void creerLeFormulaire() throws Exception {
        adminActif("awa@test.com");
        jetonAdmin = jeton("awa@test.com");
        sondageId = lire(postAvec(jetonAdmin, "/api/sondages", Map.of("titre", "Ménages", "reponseUnique", false))
                .andExpect(status().isCreated())).get("id").asLong();
        famille = lire(postAvec(jetonAdmin, url("/sections"), Map.of("titre", "Famille"))).at("/sections/0/id").asLong();
        dakar = lire(postAvec(jetonAdmin, url("/sections"), Map.of("titre", "Dakar"))).at("/sections/1/id").asLong();
        ajouter(famille, SondageIntegrationTest.question("Enfants ?", "OUI_NON", null));
        ajouter(famille, SondageIntegrationTest.question("Combien ?", "NUMERIQUE", null));
        ajouter(famille, SondageIntegrationTest.question("Région", "CHOIX_UNIQUE", null, "Dakar", "Thiès"));
        ajouter(dakar, SondageIntegrationTest.question("Quartier", "TEXTE_COURT", null));
        questions = ids(sondageId);

        conditionQuestion("Combien ?", condition("Enfants ?", "EGAL", true)).andExpect(status().isOk());
        conditionSection(dakar, condition("Région", "EGAL", "dakar")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- Éditeur

    @Test
    void conditionsRenvoyeesParIdEtStockeesParCle() throws Exception {
        JsonNode sondage = lire(getAvec(jetonAdmin, url("")).andExpect(status().isOk()));
        assertThat(sondage.at("/sections/0/questions/1/condition/combinaison").asText()).isEqualTo("ET");
        assertThat(sondage.at("/sections/0/questions/1/condition/regles/0/questionId").asLong())
                .isEqualTo(questions.get("Enfants ?"));
        assertThat(sondage.at("/sections/0/questions/1/condition/regles/0/operateur").asText()).isEqualTo("EGAL");
        assertThat(sondage.at("/sections/0/questions/1/condition/regles/0/valeur").asBoolean()).isTrue();
        assertThat(sondage.at("/sections/1/condition/regles/0/valeur").asText()).isEqualTo("dakar");
        assertThat(sondage.at("/sections/0/questions/0/condition").isNull()).isTrue();

        // En base, la source est désignée par sa clé stable.
        String cle = jdbcTemplate.queryForObject("select cle from question where id = ?", String.class,
                questions.get("Enfants ?"));
        String stockee = jdbcTemplate.queryForObject("select condition_affichage::text from question where id = ?",
                String.class, questions.get("Combien ?"));
        assertThat(objectMapper.readTree(stockee).at("/regles/0/question").asText()).isEqualTo(cle);

        // L'aperçu renvoie aussi les conditions.
        JsonNode apercu = lire(getAvec(jetonAdmin, url("/apercu")).andExpect(status().isOk()));
        assertThat(apercu.at("/sections/1/condition/regles/0/questionId").asLong()).isEqualTo(questions.get("Région"));

        // OU, plusieurs règles ; puis retrait.
        conditionQuestion("Quartier", Map.of("combinaison", "OU", "regles", List.of(
                regle("Enfants ?", "NON_REPONDUE", null), regle("Combien ?", "ENTRE", List.of(1, 3)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[1].questions[0].condition.combinaison").value("OU"))
                .andExpect(jsonPath("$.sections[1].questions[0].condition.regles", hasSize(2)))
                .andExpect(jsonPath("$.sections[1].questions[0].condition.regles[0].valeur").doesNotExist());
        deleteAvec(jetonAdmin, url("/questions/" + questions.get("Quartier") + "/condition"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[1].questions[0].condition").isEmpty());
    }

    @Test
    void referenceVersLAvantRefusee() throws Exception {
        // Enfants ? dépendrait de Combien ?, placée après.
        conditionQuestion("Enfants ?", condition("Combien ?", "REPONDUE", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONDITION_INVALIDE"))
                .andExpect(jsonPath("$.message").value(containsString("« Combien ? » doit être placée avant « Enfants ? »")));
        // Elle-même.
        conditionQuestion("Enfants ?", condition("Enfants ?", "REPONDUE", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("ne peut pas dépendre d'elle-même")));
        // Section : la source doit être dans une section précédente, pas dans la section elle-même.
        conditionSection(dakar, condition("Quartier", "REPONDUE", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("section placée avant la section « Dakar »")));
        conditionSection(famille, condition("Enfants ?", "EGAL", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONDITION_INVALIDE"));
    }

    @Test
    void operateurEtValeurCoherents() throws Exception {
        Map<String, String> refus = new LinkedHashMap<>();
        refus.put(json(condition("Enfants ?", "CONTIENT", true)), "ne s'applique pas à « Enfants ? »");
        refus.put(json(condition("Enfants ?", "EGAL", "oui")), "true (oui) ou false (non)");
        refus.put(json(condition("Région", "EGAL", "Dakar")), "l'option « Dakar » n'existe pas");
        refus.put(json(condition("Région", "PARMI", List.of())), "liste non vide");
        refus.put(json(condition("Combien ?", "ENTRE", List.of(5, 1))), "inférieure ou égale");
        refus.put(json(condition("Combien ?", "SUPERIEUR", "trois")), "un nombre");
        refus.put(json(condition("Région", "REPONDUE", "dakar")), "ne prend pas de valeur");
        refus.put(json(condition("Combien ?", "EGAL", null)), "une valeur est obligatoire");
        for (Map.Entry<String, String> cas : refus.entrySet()) {
            putJson(url("/questions/" + questions.get("Quartier") + "/condition"), cas.getKey())
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CONDITION_INVALIDE"))
                    .andExpect(jsonPath("$.message").value(containsString(cas.getValue())));
        }
        // Question d'un autre sondage, et corps invalide.
        putAvec(jetonAdmin, url("/questions/" + questions.get("Quartier") + "/condition"),
                Map.of("regles", List.of(Map.of("questionId", 999999, "operateur", "REPONDUE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("n'existe pas dans ce sondage")));
        putAvec(jetonAdmin, url("/questions/" + questions.get("Quartier") + "/condition"), Map.of("regles", List.of()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void modificationsQuiCasseraientUneConditionRefusees() throws Exception {
        // Cycle ou référence vers l'avant par réorganisation : Combien ? passerait avant Enfants ?.
        putAvec(jetonAdmin, url("/sections/" + famille + "/questions/ordre"), Map.of("ids", List.of(
                questions.get("Combien ?"), questions.get("Enfants ?"), questions.get("Région"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONDITION_DEPENDANTE"))
                .andExpect(jsonPath("$.message").value(containsString("doit être placée avant « Combien ? »")));
        // Section Dakar avant Famille.
        putAvec(jetonAdmin, url("/sections/ordre"), Map.of("ids", List.of(dakar, famille)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONDITION_DEPENDANTE"));
        // Déplacement de la source vers la section Dakar (après ses cibles).
        putAvec(jetonAdmin, url("/questions/" + questions.get("Région") + "/section"), Map.of("sectionId", dakar))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONDITION_DEPENDANTE"));
        // Suppression d'une source, d'une section qui en contient une.
        deleteAvec(jetonAdmin, url("/questions/" + questions.get("Enfants ?")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "La question « Enfants ? » est utilisée par la condition d'affichage de « Combien ? » : "
                                + "retirez d'abord cette condition."));
        deleteAvec(jetonAdmin, url("/sections/" + famille))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("la section « Dakar »")));
        // Option utilisée retirée ; type de la source changé.
        putAvec(jetonAdmin, url("/questions/" + questions.get("Région")),
                SondageIntegrationTest.question("Région", "CHOIX_UNIQUE", null, "Thiès", "Kaolack"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("l'option « dakar » n'existe pas dans « Région »")));
        putAvec(jetonAdmin, url("/questions/" + questions.get("Enfants ?")),
                SondageIntegrationTest.question("Enfants ?", "TEXTE_COURT", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONDITION_DEPENDANTE"));

        // Rien n'a changé.
        JsonNode sondage = lire(getAvec(jetonAdmin, url("")));
        assertThat(sondage.at("/sections/0/titre").asText()).isEqualTo("Famille");
        assertThat(sondage.at("/sections/0/questions/0/libelle").asText()).isEqualTo("Enfants ?");
        assertThat(sondage.at("/sections/0/questions/0/type").asText()).isEqualTo("OUI_NON");
        assertThat(sondage.at("/sections/0/questions/2/options").size()).isEqualTo(2);
        assertThat(sondage.at("/sections/0/questions/2/options/0/valeur").asText()).isEqualTo("dakar");

        // Une question sans rôle de source se supprime ; la source aussi, une fois la condition retirée.
        deleteAvec(jetonAdmin, url("/questions/" + questions.get("Combien ?") + "/condition")).andExpect(status().isOk());
        deleteAvec(jetonAdmin, url("/questions/" + questions.get("Enfants ?"))).andExpect(status().isOk());
        // La cible elle-même se supprime avec sa condition.
        deleteAvec(jetonAdmin, url("/sections/" + dakar)).andExpect(status().isOk());
    }

    @Test
    void conditionsFigeesALaPublication() throws Exception {
        publier();
        conditionQuestion("Quartier", condition("Enfants ?", "REPONDUE", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SONDAGE_PUBLIE"));
        deleteAvec(jetonAdmin, url("/questions/" + questions.get("Combien ?") + "/condition"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SONDAGE_PUBLIE"));
    }

    // ---------------------------------------------------------------- Collecte

    @Test
    void formulairePublicRenvoieLesConditionsSansCle() throws Exception {
        String lien = publier();
        JsonNode formulaire = lire(mockMvc.perform(get("/api/public/sondages/" + lien)).andExpect(status().isOk()));
        assertThat(formulaire.at("/sections/0/questions/1/condition/regles/0/questionId").asLong())
                .isEqualTo(questions.get("Enfants ?"));
        assertThat(formulaire.at("/sections/1/condition/regles/0/valeur").asText()).isEqualTo("dakar");
        assertThat(formulaire.at("/sections/0/questions/1/condition/regles/0").has("cle")).isFalse();
        assertThat(formulaire.at("/sections/0/questions/1/condition/regles/0").has("question")).isFalse();
    }

    @Test
    void visibiliteRecalculeeParLeServeur() throws Exception {
        String lien = publier();

        // Masquées : Combien ? (Enfants = Non) et la section Dakar (Région = thies). Obligatoires mais non exigées ;
        // les réponses envoyées pour elles sont ignorées et non enregistrées.
        repondre(lien, Map.of("Enfants ?", false, "Combien ?", 4, "Région", "thies", "Quartier", "Médina"))
                .andExpect(status().isCreated());
        assertThat(compter("select count(*) from reponse_question")).isEqualTo(2);
        assertThat(compter("select count(*) from reponse_question where question_id in ("
                + questions.get("Combien ?") + ", " + questions.get("Quartier") + ")")).isZero();
        assertThat(compter("select count(*) from reponse_question_masquee")).isEqualTo(2);

        // Une réponse à une question masquée n'est même pas validée (valeur incohérente ignorée).
        repondre(lien, Map.of("Enfants ?", false, "Combien ?", "beaucoup", "Région", "thies"))
                .andExpect(status().isCreated());

        // Visibles : redeviennent obligatoires.
        repondre(lien, Map.of("Enfants ?", true, "Région", "dakar"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPONSES_INVALIDES"))
                .andExpect(jsonPath("$.erreurs", hasSize(2)))
                .andExpect(jsonPath("$.erreurs[0].questionId").value(questions.get("Combien ?")))
                .andExpect(jsonPath("$.erreurs[1].questionId").value(questions.get("Quartier")));

        repondre(lien, Map.of("Enfants ?", true, "Combien ?", 2, "Région", "dakar", "Quartier", "Médina"))
                .andExpect(status().isCreated());
        assertThat(compter("select count(*) from reponse")).isEqualTo(3);
        assertThat(compter("select count(*) from reponse_question_masquee")).isEqualTo(4);
    }

    // ---------------------------------------------------------------- Copies

    @Test
    void conditionsSuiventLesNouvellesQuestionsDansLesCopies() throws Exception {
        publier();
        long version2 = lire(postAvec(jetonAdmin, url("/nouvelle-version"), Map.of()).andExpect(status().isCreated()))
                .get("id").asLong();
        long copie = lire(postAvec(jetonAdmin, url("/dupliquer"), Map.of()).andExpect(status().isCreated()))
                .get("id").asLong();
        long reutilisation = lire(postAvec(jetonAdmin, "/api/sondages",
                Map.of("titre", "Réutilisé", "sondageSourceId", sondageId)).andExpect(status().isCreated()))
                .get("id").asLong();

        for (long id : List.of(version2, copie, reutilisation)) {
            Map<String, Long> nouvelles = ids(id);
            assertThat(nouvelles.get("Enfants ?")).isNotEqualTo(questions.get("Enfants ?"));
            JsonNode sondage = lire(getAvec(jetonAdmin, "/api/sondages/" + id));
            assertThat(sondage.at("/sections/0/questions/1/condition/regles/0/questionId").asLong())
                    .as("sondage %d", id).isEqualTo(nouvelles.get("Enfants ?"));
            assertThat(sondage.at("/sections/1/condition/regles/0/questionId").asLong())
                    .isEqualTo(nouvelles.get("Région"));
        }
        // La nouvelle version reste modifiable et ses conditions se vérifient comme les autres.
        putAvec(jetonAdmin, "/api/sondages/" + version2 + "/sections/ordre",
                Map.of("ids", List.of(lire(getAvec(jetonAdmin, "/api/sondages/" + version2)).at("/sections/1/id").asLong(),
                        lire(getAvec(jetonAdmin, "/api/sondages/" + version2)).at("/sections/0/id").asLong())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONDITION_DEPENDANTE"));
    }

    // ---------------------------------------------------------------- Statistiques et exports

    @Test
    void statistiquesVuesEtExportVide() throws Exception {
        String lien = publier();
        repondre(lien, Map.of("Enfants ?", true, "Combien ?", 2, "Région", "dakar", "Quartier", "Médina"))
                .andExpect(status().isCreated());
        repondre(lien, Map.of("Enfants ?", false, "Région", "thies")).andExpect(status().isCreated());
        repondre(lien, Map.of("Enfants ?", false, "Région", "dakar", "Quartier", "Plateau")).andExpect(status().isCreated());

        Map<String, JsonNode> stats = new HashMap<>();
        lire(getAvec(jetonAdmin, url("/statistiques")).andExpect(status().isOk())).get("questions")
                .forEach(q -> stats.put(q.get("libelle").asText(), q));

        assertThat(stats.get("Enfants ?").get("conditionnelle").asBoolean()).isFalse();
        assertThat(stats.get("Enfants ?").get("vues").asLong()).isEqualTo(3);
        // Combien ? : vue par 1 répondant (qui y a répondu) ; les 2 autres ne l'ont pas vue : pas « sans réponse ».
        assertThat(stats.get("Combien ?").get("conditionnelle").asBoolean()).isTrue();
        assertThat(stats.get("Combien ?").get("vues").asLong()).isEqualTo(1);
        assertThat(stats.get("Combien ?").get("nombreRepondants").asLong()).isEqualTo(1);
        assertThat(stats.get("Combien ?").get("sansReponse").asLong()).isZero();
        // Quartier : section conditionnelle, vue par 2.
        assertThat(stats.get("Quartier").get("conditionnelle").asBoolean()).isTrue();
        assertThat(stats.get("Quartier").get("vues").asLong()).isEqualTo(2);
        assertThat(stats.get("Quartier").get("nombreRepondants").asLong()).isEqualTo(2);

        // Export : cellule vide quand la question était masquée.
        String csv = new String(getAvec(jetonAdmin, url("/export/reponses?format=CSV")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8).substring(1);
        String[] lignes = csv.split("\r\n");
        List<String> entetes = Arrays.asList(lignes[0].split(";", -1));
        int combien = entetes.indexOf("Combien ?");
        int quartier = entetes.indexOf("Quartier");
        List<String> valeursCombien = new ArrayList<>();
        List<String> valeursQuartier = new ArrayList<>();
        for (int i = 1; i < lignes.length; i++) {
            String[] cellules = lignes[i].split(";", -1);
            valeursCombien.add(cellules[combien]);
            valeursQuartier.add(cellules[quartier]);
        }
        assertThat(valeursCombien).containsExactlyInAnyOrder("2", "", "");
        assertThat(valeursQuartier).containsExactlyInAnyOrder("Médina", "", "Plateau");

        // Rapport PDF : il se génère avec la mention « vue par ».
        getAvec(jetonAdmin, url("/export/statistiques?format=PDF")).andExpect(status().isOk());
        getAvec(jetonAdmin, url("/export/statistiques?format=XLSX")).andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- Outils

    private String url(String suite) {
        return "/api/sondages/" + sondageId + suite;
    }

    private void ajouter(long section, Map<String, Object> question) throws Exception {
        postAvec(jetonAdmin, url("/sections/" + section + "/questions"), question).andExpect(status().isCreated());
    }

    private Map<String, Object> regle(String libelle, String operateur, Object valeur) {
        Map<String, Object> regle = new HashMap<>();
        regle.put("questionId", questions.get(libelle));
        regle.put("operateur", operateur);
        if (valeur != null) {
            regle.put("valeur", valeur);
        }
        return regle;
    }

    private Map<String, Object> condition(String libelle, String operateur, Object valeur) {
        return Map.of("regles", List.of(regle(libelle, operateur, valeur)));
    }

    private ResultActions conditionQuestion(String libelle, Object condition) throws Exception {
        return putAvec(jetonAdmin, url("/questions/" + questions.get(libelle) + "/condition"), condition);
    }

    private ResultActions conditionSection(long section, Object condition) throws Exception {
        return putAvec(jetonAdmin, url("/sections/" + section + "/condition"), condition);
    }

    private ResultActions putJson(String chemin, String corps) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(chemin)
                .header("Authorization", "Bearer " + jetonAdmin)
                .contentType(MediaType.APPLICATION_JSON).content(corps));
    }

    /** Publie le sondage et renvoie le jeton de son lien public. */
    private String publier() throws Exception {
        postAvec(jetonAdmin, url("/publier"), Map.of()).andExpect(status().isOk());
        return lire(getAvec(jetonAdmin, url("/lien")).andExpect(status().isOk())).get("jeton").asText();
    }

    private ResultActions repondre(String lien, Map<String, Object> parLibelle) throws Exception {
        List<Map<String, Object>> reponses = new ArrayList<>();
        parLibelle.forEach((libelle, valeur) -> reponses.add(Map.of("questionId", questions.get(libelle), "valeur", valeur)));
        return mockMvc.perform(post("/api/public/sondages/" + lien + "/reponses")
                .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("reponses", reponses))));
    }

    private Map<String, Long> ids(long id) throws Exception {
        Map<String, Long> ids = new HashMap<>();
        lire(getAvec(jetonAdmin, "/api/sondages/" + id)).get("sections")
                .forEach(s -> s.get("questions").forEach(q -> ids.put(q.get("libelle").asText(), q.get("id").asLong())));
        return ids;
    }

    private long compter(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
