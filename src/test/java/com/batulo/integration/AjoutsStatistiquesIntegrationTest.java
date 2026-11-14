package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ajouts pour les statistiques et le formulaire public : CORS (Content-Disposition, X-Jeton-Repondant), réponses
 * par lignée ou par version, formulaire public sans réglage d'analyse, dejaRepondu.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class AjoutsStatistiquesIntegrationTest extends PostgresIntegrationTestBase {

    private static final String ORIGINE = "http://localhost:4200";
    private static final String EN_TETE_JETON = "X-Jeton-Repondant";

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Utilisateur admin;
    private String jetonAdmin;

    @BeforeEach
    void preparer() throws Exception {
        admin = adminActif("awa@test.com");
        jetonAdmin = jeton("awa@test.com");
    }

    // ------------------------------------------------------------------ CORS

    @Test
    void corsExposeContentDispositionEtAccepteLeJetonRepondant() throws Exception {
        long id = sondagePublie("Export", null);
        repondre(id, UUID.randomUUID().toString());

        getAvec(jetonAdmin, "/api/sondages/" + id + "/export/reponses?format=CSV")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")));
        mockMvc.perform(get("/api/sondages/" + id + "/export/reponses?format=CSV")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAdmin)
                        .header(HttpHeaders.ORIGIN, ORIGINE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGINE))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString(HttpHeaders.CONTENT_DISPOSITION)));

        // Pré-vérification du navigateur pour le formulaire public avec l'en-tête du jeton (sans authentification).
        mockMvc.perform(options("/api/public/sondages/" + jetonPublic(id))
                        .header(HttpHeaders.ORIGIN, ORIGINE)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, EN_TETE_JETON))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsString(EN_TETE_JETON)));
        // Une origine inconnue reste refusée.
        mockMvc.perform(options("/api/public/sondages/x")
                        .header(HttpHeaders.ORIGIN, "https://inconnu.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ Réponses par version ou par lignée

    @Test
    void reponsesDeLaVersionDeLaLigneeOuDeLaVersionN() throws Exception {
        long v1 = sondagePublie("Satisfaction", null);
        repondre(v1, UUID.randomUUID().toString());
        repondre(v1, UUID.randomUUID().toString());
        long v2 = nouvelleVersion(v1);
        publier(v2);
        for (int i = 0; i < 3; i++) {
            repondre(v2, UUID.randomUUID().toString());
        }

        // Par défaut : cette version.
        JsonNode deV2 = lire(getAvec(jetonAdmin, "/api/sondages/" + v2 + "/reponses").andExpect(status().isOk()));
        assertThat(deV2.get("totalElements").asLong()).isEqualTo(3);
        assertThat(versions(deV2)).containsOnly(2);
        assertThat(deV2.at("/contenu/0/sondageId").asLong()).isEqualTo(v2);
        assertThat(deV2.at("/contenu/0/valeurs/0/valeur").asBoolean()).isTrue();
        assertThat(lire(getAvec(jetonAdmin, "/api/sondages/" + v1 + "/reponses")).get("totalElements").asLong())
                .isEqualTo(2);

        // Toute la lignée, depuis n'importe quelle version ; les plus récentes d'abord.
        for (long depuis : List.of(v1, v2)) {
            JsonNode lignee = lire(getAvec(jetonAdmin, "/api/sondages/" + depuis + "/reponses?lignee=true"));
            assertThat(lignee.get("totalElements").asLong()).isEqualTo(5);
            assertThat(versions(lignee)).containsExactly(2, 2, 2, 1, 1);
            assertThat(lignee.at("/contenu/4/sondageId").asLong()).isEqualTo(v1);
        }

        // La version N de la lignée, depuis n'importe quelle version.
        JsonNode version1 = lire(getAvec(jetonAdmin, "/api/sondages/" + v2 + "/reponses?version=1"));
        assertThat(version1.get("totalElements").asLong()).isEqualTo(2);
        assertThat(versions(version1)).containsOnly(1);
        assertThat(lire(getAvec(jetonAdmin, "/api/sondages/" + v1 + "/reponses?version=2")).get("totalElements")
                .asLong()).isEqualTo(3);

        // Pagination.
        JsonNode page = lire(getAvec(jetonAdmin, "/api/sondages/" + v1 + "/reponses?lignee=true&taille=2&page=2"));
        assertThat(page.get("contenu")).hasSize(1);
        assertThat(page.get("totalPages").asInt()).isEqualTo(3);

        getAvec(jetonAdmin, "/api/sondages/" + v1 + "/reponses?version=9")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("VERSION_INTROUVABLE"));
        getAvec(jetonAdmin, "/api/sondages/" + v1 + "/reponses?lignee=true&version=1")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
        getAvec(jetonAdmin, "/api/sondages/" + v1 + "/reponses?version=0").andExpect(status().isBadRequest());
    }

    @Test
    void reponsesDroitsEtIsolationInchanges() throws Exception {
        long v1 = sondagePublie("Droits", null);
        repondre(v1, UUID.randomUUID().toString());
        long v2 = nouvelleVersion(v1);
        publier(v2);
        repondre(v2, UUID.randomUUID().toString());

        Utilisateur assigne = gestionnaire("assigne@test.com", admin);
        gestionnaire("autre-g@test.com", admin);
        postAvec(jetonAdmin, "/api/sondages/" + v1 + "/assignations",
                Map.of("gestionnaireIds", List.of(assigne.getId()))).andExpect(status().isOk());
        getAvec(jeton("assigne@test.com"), "/api/sondages/" + v2 + "/reponses?lignee=true")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        getAvec(jeton("assigne@test.com"), "/api/sondages/" + v2 + "/reponses?version=1")
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));

        String jetonAutreGestionnaire = jeton("autre-g@test.com");
        adminActif("b@test.com");
        String jetonAutreEntreprise = jeton("b@test.com");
        for (String parametres : List.of("", "?lignee=true", "?version=1")) {
            getAvec(jetonAutreGestionnaire, "/api/sondages/" + v2 + "/reponses" + parametres)
                    .andExpect(status().isNotFound());
            getAvec(jetonAutreEntreprise, "/api/sondages/" + v2 + "/reponses" + parametres)
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void reponsesSansRequeteParReponse() throws Exception {
        long v1 = sondagePublie("Comptage", null);
        repondre(v1, UUID.randomUUID().toString());
        long v2 = nouvelleVersion(v1);
        publier(v2);
        repondre(v2, UUID.randomUUID().toString());
        long avecDeux = requetesPour("/api/sondages/" + v2 + "/reponses?lignee=true&taille=100");

        for (int i = 0; i < 8; i++) {
            repondre(v2, UUID.randomUUID().toString());
        }
        assertThat(requetesPour("/api/sondages/" + v2 + "/reponses?lignee=true&taille=100")).isEqualTo(avecDeux);
        assertThat(requetesPour("/api/sondages/" + v2 + "/reponses?version=1&taille=100"))
                .isLessThanOrEqualTo(avecDeux + 1);
    }

    // ------------------------------------------------------------------ Formulaire public

    @Test
    void formulairePublicSansReglageDAnalyse() throws Exception {
        long id = brouillonAvecQuestion("Critères", true);
        long ageId = ajouterQuestion(id, SondageIntegrationTest.question("Âge", "NUMERIQUE", null));
        putAvec(jetonAdmin, "/api/sondages/" + id + "/questions/" + ageId + "/critere",
                Map.of("estCritere", true, "critereConfig", Map.of("tranches", List.of(18, 30, 45))))
                .andExpect(status().isOk());
        publier(id);

        String brut = mockMvc.perform(get("/api/public/sondages/" + jetonPublic(id)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(brut).doesNotContain("estCritere", "critereConfig", "tranches", "\"cle\"");
        JsonNode age = objectMapper.readTree(brut).at("/sections/0/questions/1");
        assertThat(age.get("id").asLong()).isEqualTo(ageId);
        assertThat(age.get("libelle").asText()).isEqualTo("Âge");
        assertThat(age.get("type").asText()).isEqualTo("NUMERIQUE");
        assertThat(age.has("configuration")).isTrue();

        // L'éditeur garde les réglages.
        assertThat(lire(getAvec(jetonAdmin, "/api/sondages/" + id)).at("/sections/0/questions/1/critereConfig/tranches")
                .size()).isEqualTo(3);
    }

    @Test
    void optionsDuFormulairePublic() throws Exception {
        long id = lire(postAvec(jetonAdmin, "/api/sondages", Map.of("titre", "Options"))).get("id").asLong();
        long sectionId = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/sections", Map.of("titre", "S")))
                .at("/sections/0/id").asLong();
        postAvec(jetonAdmin, "/api/sondages/" + id + "/sections/" + sectionId + "/questions",
                SondageIntegrationTest.question("Région", "CHOIX_UNIQUE", null, "Kayes", "Kaolack"))
                .andExpect(status().isCreated());
        publier(id);
        JsonNode option = lire(mockMvc.perform(get("/api/public/sondages/" + jetonPublic(id))))
                .at("/sections/0/questions/0/options/0");
        assertThat(option.get("id").isNumber()).isTrue();
        assertThat(option.get("libelle").asText()).isEqualTo("Kayes");
        assertThat(option.get("valeur").asText()).isEqualTo("kayes");
        assertThat(option.get("ordre").asInt()).isEqualTo(1);
    }

    @Test
    void dejaReponduSelonLEnTeteDuJeton() throws Exception {
        long v1 = sondagePublie("Unique", null);
        String lien = jetonPublic(v1);
        String appareil = UUID.randomUUID().toString();

        formulaire(lien, null).andExpect(jsonPath("$.reponseUnique").value(true))
                .andExpect(jsonPath("$.dejaRepondu").value(false));
        formulaire(lien, "pas-un-uuid").andExpect(status().isOk()).andExpect(jsonPath("$.dejaRepondu").value(false));
        formulaire(lien, appareil).andExpect(jsonPath("$.dejaRepondu").value(false));

        repondre(v1, appareil);
        formulaire(lien, appareil).andExpect(jsonPath("$.dejaRepondu").value(true))
                .andExpect(jsonPath("$.sections[0].questions[0].libelle").value("Q"));
        // Même jeton, autre casse et espaces : même appareil (comme à l'envoi).
        formulaire(lien, " " + appareil.toUpperCase() + " ").andExpect(jsonPath("$.dejaRepondu").value(true));
        formulaire(lien, UUID.randomUUID().toString()).andExpect(jsonPath("$.dejaRepondu").value(false));
        formulaire(lien, null).andExpect(jsonPath("$.dejaRepondu").value(false));

        // Sur toute la lignée : la v2 ouverte par le même lien sait que l'appareil a répondu à la v1.
        long v2 = nouvelleVersion(v1);
        publier(v2);
        formulaire(lien, appareil).andExpect(jsonPath("$.dejaRepondu").value(true));

        // Le POST garde ses règles : 409 pour cet appareil, 400 sans jeton valide.
        envoyer(lien, appareil, questionId(v2)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEJA_REPONDU"));
        envoyer(lien, "pas-un-uuid", questionId(v2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JETON_REPONDANT_INVALIDE"));
    }

    @Test
    void dejaReponduAbsentSansReponseUnique() throws Exception {
        long id = sondagePublie("Terrain", false);
        String lien = jetonPublic(id);
        String brut = formulaire(lien, UUID.randomUUID().toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reponseUnique").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(brut).has("dejaRepondu")).isFalse();
    }

    // ------------------------------------------------------------------ Outils

    private long brouillonAvecQuestion(String titre, Boolean reponseUnique) throws Exception {
        Map<String, Object> creation = new HashMap<>();
        creation.put("titre", titre);
        if (reponseUnique != null) {
            creation.put("reponseUnique", reponseUnique);
        }
        long id = lire(postAvec(jetonAdmin, "/api/sondages", creation).andExpect(status().isCreated()))
                .get("id").asLong();
        postAvec(jetonAdmin, "/api/sondages/" + id + "/sections", Map.of("titre", "S")).andExpect(status().isCreated());
        ajouterQuestion(id, SondageIntegrationTest.question("Q", "OUI_NON", null));
        return id;
    }

    /** Ajoute la question à la première section et renvoie son id. */
    private long ajouterQuestion(long sondageId, Map<String, Object> question) throws Exception {
        long sectionId = lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId)).at("/sections/0/id").asLong();
        JsonNode questions = lire(postAvec(jetonAdmin, "/api/sondages/" + sondageId + "/sections/" + sectionId
                + "/questions", question).andExpect(status().isCreated())).at("/sections/0/questions");
        return questions.get(questions.size() - 1).get("id").asLong();
    }

    private long sondagePublie(String titre, Boolean reponseUnique) throws Exception {
        long id = brouillonAvecQuestion(titre, reponseUnique);
        publier(id);
        return id;
    }

    private void publier(long id) throws Exception {
        postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
    }

    private long nouvelleVersion(long sondageId) throws Exception {
        return lire(postAvec(jetonAdmin, "/api/sondages/" + sondageId + "/nouvelle-version", Map.of())
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private String jetonPublic(long id) throws Exception {
        return lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/lien").andExpect(status().isOk()))
                .get("jeton").asText();
    }

    private long questionId(long sondageId) throws Exception {
        return lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId)).at("/sections/0/questions/0/id").asLong();
    }

    /** Une réponse « oui » à la version EN_COURS de la lignée (celle de ce sondage). */
    private void repondre(long sondageId, String appareil) throws Exception {
        envoyer(jetonPublic(sondageId), appareil, questionId(sondageId)).andExpect(status().isCreated());
    }

    private ResultActions envoyer(String lien, String appareil, long questionId) throws Exception {
        Map<String, Object> corps = new HashMap<>();
        corps.put("jetonRepondant", appareil);
        corps.put("reponses", List.of(Map.of("questionId", questionId, "valeur", true)));
        return mockMvc.perform(post("/api/public/sondages/" + lien + "/reponses")
                .contentType(MediaType.APPLICATION_JSON).content(json(corps)));
    }

    private ResultActions formulaire(String lien, String appareil) throws Exception {
        var requete = get("/api/public/sondages/" + lien);
        if (appareil != null) {
            requete.header(EN_TETE_JETON, appareil);
        }
        return mockMvc.perform(requete);
    }

    private static List<Integer> versions(JsonNode page) {
        List<Integer> versions = new ArrayList<>();
        page.get("contenu").forEach(r -> versions.add(r.get("version").asInt()));
        return versions;
    }

    private long requetesPour(String url) throws Exception {
        Statistics statistiques = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistiques.clear();
        getAvec(jetonAdmin, url).andExpect(status().isOk());
        return statistiques.getPrepareStatementCount();
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
