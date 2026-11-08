package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Étape 2.4 : collecte publique des réponses (RE-01 à RE-03), lien, QR code et suivi. */
class CollecteIntegrationTest extends PostgresIntegrationTestBase {

    private Utilisateur admin;
    private String jetonAdmin;
    private long sondageId;
    private String lien;
    private Map<String, Long> questions;

    @BeforeEach
    void publierUnSondageAvecLesDixTypes() throws Exception {
        admin = adminActif("awa@test.com");
        jetonAdmin = jeton("awa@test.com");
        sondageId = creerSondageDixTypes(Map.of("titre", "Enquête agricole"));
        postAvec(jetonAdmin, "/api/sondages/" + sondageId + "/publier", Map.of()).andExpect(status().isOk());
        lien = jetonPublic(sondageId);
        questions = idsDesQuestions(sondageId);
    }

    @Test
    void formulairePublicSansDonneesInternes() throws Exception {
        JsonNode formulaire = lire(mockMvc.perform(get("/api/public/sondages/" + lien)).andExpect(status().isOk()));

        assertThat(formulaire.get("titre").asText()).isEqualTo("Enquête agricole");
        assertThat(formulaire.get("reponseUnique").asBoolean()).isTrue();
        assertThat(formulaire.at("/sections/0/questions").size()).isEqualTo(10);
        assertThat(formulaire.at("/sections/0/questions/0/options/0/valeur").asText()).isEqualTo("kaolack");
        for (String interne : List.of("id", "entrepriseId", "creePar", "version", "statut", "sondageOrigineId",
                "dateCreation", "publie", "estModele")) {
            assertThat(formulaire.has(interne)).as(interne).isFalse();
        }
        mockMvc.perform(get("/api/public/sondages/jeton-inconnu"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SONDAGE_INTROUVABLE"));
    }

    @Test
    void reponseValideAuxDixTypesRangeeParColonne() throws Exception {
        repondre(UUID.randomUUID().toString(), reponsesValides())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Merci, votre réponse a été enregistrée."));

        assertThat(compter("select count(*) from reponse")).isEqualTo(1);
        // CHOIX_MULTIPLE : une ligne par option cochée ; les 10 questions donnent donc 11 lignes.
        assertThat(compter("select count(*) from reponse_question")).isEqualTo(11);
        assertThat(compter("select count(*) from reponse_question where option_id is not null")).isEqualTo(3);
        assertThat(compter("select count(*) from reponse_question where valeur_booleenne = true")).isEqualTo(1);
        assertThat(compter("select count(*) from reponse_question where valeur_numerique in (4, 12.5)")).isEqualTo(2);
        assertThat(compter("select count(*) from reponse_question where valeur_date = '2026-07-14'")).isEqualTo(1);
        assertThat(compter("select count(*) from reponse_question where valeur_texte is not null")).isEqualTo(2);
        assertThat(compter("select count(*) from reponse_question where jsonb_typeof(valeur_json) = 'array'")).isEqualTo(1);
        assertThat(compter("select count(*) from reponse_question where jsonb_typeof(valeur_json) = 'object'")).isEqualTo(1);

        // Le suivi relit les valeurs dans le format de l'envoi, dans l'ordre du formulaire.
        JsonNode page = lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/reponses").andExpect(status().isOk()));
        JsonNode valeurs = page.at("/contenu/0/valeurs");
        assertThat(valeurs.size()).isEqualTo(10);
        Map<String, Object> attendues = reponsesValides();
        for (JsonNode valeur : valeurs) {
            String libelle = valeur.get("libelle").asText();
            assertThat(valeur.get("valeur")).as(libelle).isEqualTo(objectMapper.valueToTree(attendues.get(libelle)));
        }
    }

    static Stream<Object[]> reponsesInvalides() {
        return Stream.<Object[]>of(
                cas("Région", "inconnue", "n'existe pas pour cette question"),
                cas("Région", List.of("kaolack"), "valeur d'option"),
                cas("Cultures", List.of("mil", "mil"), "choisie deux fois"),
                cas("Engrais ?", "oui", "true (oui) ou false (non)"),
                cas("Village", "x".repeat(101), "100 caractères"),
                cas("Satisfaction", 11, "de 1 à 10"),
                cas("Satisfaction", 2.5, "de 1 à 10"),
                cas("Surface (ha)", 600, "comprise entre 0 et 500"),
                cas("Surface (ha)", 12.55, "1 chiffre(s) après la virgule"),
                cas("Semis", "2026-12-01", "2026-09-30 ou avant"),
                cas("Semis", "14/07/2026", "AAAA-MM-JJ"),
                cas("Priorités", List.of("engrais", "semences"), "toutes les options"),
                cas("Priorités", List.of("engrais", "engrais", "credit"), "deux fois"),
                cas("Services", Map.of("Vulgarisation", "Bon"), "Ligne « Marché » : choisissez une colonne"),
                cas("Services", Map.of("Vulgarisation", "Bon", "Marché", "Excellent"), "« Excellent » n'existe pas"),
                cas("Services", Map.of("Vulgarisation", "Bon", "Marché", "Bon", "Autre", "Bon"), "« Autre » n'existe pas"));
    }

    private static Object[] cas(String libelle, Object valeur, String message) {
        return new Object[]{libelle, valeur, message};
    }

    @ParameterizedTest
    @MethodSource("reponsesInvalides")
    void validationParQuestion(String libelle, Object valeur, String message) throws Exception {
        Map<String, Object> reponses = new LinkedHashMap<>(reponsesValides());
        reponses.put(libelle, valeur);

        repondre(UUID.randomUUID().toString(), reponses)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPONSES_INVALIDES"))
                .andExpect(jsonPath("$.erreurs", hasSize(1)))
                .andExpect(jsonPath("$.erreurs[0].questionId").value(questions.get(libelle)))
                .andExpect(jsonPath("$.erreurs[0].message").value(containsString(message)));
        assertThat(compter("select count(*) from reponse")).isZero();
    }

    @Test
    void toutesLesErreursSontRenvoyeesEnUneFois() throws Exception {
        // Aucune réponse : chaque question obligatoire est signalée (8 sur 10 ; Village et Semis sont facultatives).
        JsonNode erreur = lire(repondre(UUID.randomUUID().toString(), Map.of()).andExpect(status().isBadRequest()));
        assertThat(erreur.get("erreurs").size()).isEqualTo(8);
        assertThat(erreur.at("/erreurs/0/message").asText()).isEqualTo("Cette question est obligatoire.");

        // Question inconnue de cette version, ou envoyée deux fois.
        List<Map<String, Object>> liste = new ArrayList<>(liste(reponsesValides()));
        liste.add(Map.of("questionId", 999999, "valeur", "x"));
        liste.add(Map.of("questionId", questions.get("Région"), "valeur", "fatick"));
        JsonNode erreurs = lire(envoyer(Map.of("jetonRepondant", UUID.randomUUID().toString(), "reponses", liste))
                .andExpect(status().isBadRequest()));
        assertThat(erreurs.toString()).contains("n'existe pas dans ce sondage", "deux réponses");
    }

    @Test
    void uneSeuleReponseParAppareilEnModeEnLigne() throws Exception {
        String appareil = UUID.randomUUID().toString();
        repondre(appareil, reponsesValides()).andExpect(status().isCreated());
        repondre(appareil, reponsesValides())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEJA_REPONDU"));
        repondre(UUID.randomUUID().toString(), reponsesValides()).andExpect(status().isCreated());
        repondre(null, reponsesValides())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JETON_REPONDANT_INVALIDE"));
        repondre("pas-un-uuid", reponsesValides()).andExpect(status().isBadRequest());
        assertThat(compter("select count(*) from reponse")).isEqualTo(2);
    }

    @Test
    void collecteTerrainPlusieursReponsesDuMemeAppareil() throws Exception {
        Map<String, Object> creation = new HashMap<>(Map.of("titre", "Collecte terrain", "reponseUnique", false));
        long terrain = creerSondageDixTypes(creation);
        postAvec(jetonAdmin, "/api/sondages/" + terrain + "/publier", Map.of()).andExpect(status().isOk());
        lien = jetonPublic(terrain);
        questions = idsDesQuestions(terrain);

        String appareil = UUID.randomUUID().toString();
        repondre(appareil, reponsesValides()).andExpect(status().isCreated());
        repondre(appareil, reponsesValides()).andExpect(status().isCreated());
        repondre(null, reponsesValides()).andExpect(status().isCreated());
        assertThat(compter("select count(*) from reponse where sondage_id = " + terrain)).isEqualTo(3);
        assertThat(compter("select count(*) from reponse where jeton_repondant_hash is not null")).isZero();

        // Figé à la publication.
        putAvec(jetonAdmin, "/api/sondages/" + terrain, Map.of("titre", "X", "reponseUnique", true))
                .andExpect(status().isConflict());
    }

    @Test
    void lienStableParLigneeEtUniciteSurToutesLesVersions() throws Exception {
        String appareil = UUID.randomUUID().toString();
        repondre(appareil, reponsesValides()).andExpect(status().isCreated());

        long v2 = lire(postAvec(jetonAdmin, "/api/sondages/" + sondageId + "/nouvelle-version", Map.of()))
                .get("id").asLong();
        putAvec(jetonAdmin, "/api/sondages/" + v2, Map.of("titre", "Enquête agricole (v2)")).andExpect(status().isOk());
        postAvec(jetonAdmin, "/api/sondages/" + v2 + "/publier", Map.of()).andExpect(status().isOk());

        // Même lien, il mène à la v2.
        assertThat(jetonPublic(v2)).isEqualTo(lien);
        mockMvc.perform(get("/api/public/sondages/" + lien))
                .andExpect(jsonPath("$.titre").value("Enquête agricole (v2)"));
        // Le même appareil ne répond pas une 2e fois au même sondage, même dans une nouvelle version.
        questions = idsDesQuestions(v2);
        repondre(appareil, reponsesValides()).andExpect(status().isConflict());
        repondre(UUID.randomUUID().toString(), reponsesValides()).andExpect(status().isCreated());

        // Chaque réponse reste liée à sa version.
        getAvec(jetonAdmin, "/api/sondages/" + v2 + "/collecte")
                .andExpect(jsonPath("$.nombreReponses").value(1))
                .andExpect(jsonPath("$.nombreReponsesTotal").value(2));
        getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/collecte")
                .andExpect(jsonPath("$.nombreReponses").value(1))
                .andExpect(jsonPath("$.collecteOuverte").value(false));
    }

    @Test
    void collecteFermeeSelonLeStatutEtLAbonnement() throws Exception {
        // Brouillon : pas encore de lien.
        long brouillon = creerSondageDixTypes(Map.of("titre", "Brouillon"));
        getAvec(jetonAdmin, "/api/sondages/" + brouillon + "/lien")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SONDAGE_BROUILLON"));

        // Planifié : 403 SONDAGE_NON_OUVERT.
        postAvec(jetonAdmin, "/api/sondages/" + brouillon + "/publier",
                Map.of("datePublication", Instant.now().plus(Duration.ofDays(2)).toString())).andExpect(status().isOk());
        String lienPlanifie = jetonPublic(brouillon);
        mockMvc.perform(get("/api/public/sondages/" + lienPlanifie))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SONDAGE_NON_OUVERT"))
                .andExpect(jsonPath("$.message").value(containsString("ouvrira le")));

        // Abonnement expiré : la collecte est fermée (410), le suivi reste consultable.
        jdbcTemplate.update("update abonnement set date_expiration = ? where entreprise_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1))), admin.getEntreprise().getId());
        mockMvc.perform(get("/api/public/sondages/" + lien))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SONDAGE_FERME"));
        repondre(UUID.randomUUID().toString(), reponsesValides()).andExpect(status().isGone());
        getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/collecte")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collecteOuverte").value(false))
                .andExpect(jsonPath("$.raisonFermeture").value(containsString("abonnement")));
        jdbcTemplate.update("update abonnement set date_expiration = ? where entreprise_id = ?",
                Timestamp.from(Instant.now().plus(Duration.ofDays(30))), admin.getEntreprise().getId());

        // Terminé : 410.
        postAvec(jetonAdmin, "/api/sondages/" + sondageId + "/fermer", Map.of()).andExpect(status().isOk());
        repondre(UUID.randomUUID().toString(), reponsesValides())
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SONDAGE_FERME"));
    }

    @Test
    void lienEtQrCode() throws Exception {
        JsonNode lienSondage = lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/lien").andExpect(status().isOk()));
        assertThat(lienSondage.get("jeton").asText()).hasSize(43).doesNotContain(String.valueOf(sondageId));
        assertThat(lienSondage.get("url").asText()).isEqualTo("http://localhost:4200/s/" + lien);

        byte[] png = getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/qr-code?taille=300")
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();
        var image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(300);
        String decode = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(
                new BufferedImageLuminanceSource(image)))).getText();
        assertThat(decode).isEqualTo("http://localhost:4200/s/" + lien);

        getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/qr-code?taille=50").andExpect(status().isBadRequest());
    }

    @Test
    void suiviReserveAuxComptesQuiVoientLeSondage() throws Exception {
        Utilisateur assigne = gestionnaire("g1@test.com", admin, false);
        gestionnaire("g2@test.com", admin, true);
        postAvec(jetonAdmin, "/api/sondages/" + sondageId + "/assignations",
                Map.of("gestionnaireIds", List.of(assigne.getId()))).andExpect(status().isOk());
        repondre(UUID.randomUUID().toString(), reponsesValides()).andExpect(status().isCreated());
        repondre(UUID.randomUUID().toString(), reponsesValides()).andExpect(status().isCreated());

        String jetonG1 = jeton("g1@test.com");
        getAvec(jetonG1, "/api/sondages/" + sondageId + "/collecte")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreReponses").value(2))
                .andExpect(jsonPath("$.collecteOuverte").value(true))
                .andExpect(jsonPath("$.derniereReponse").exists());
        getAvec(jetonG1, "/api/sondages/" + sondageId + "/reponses?taille=1")
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.contenu", hasSize(1)));
        getAvec(jetonG1, "/api/sondages/" + sondageId + "/lien").andExpect(status().isOk());

        String jetonG2 = jeton("g2@test.com");
        getAvec(jetonG2, "/api/sondages/" + sondageId + "/collecte").andExpect(status().isNotFound());
        getAvec(jetonG2, "/api/sondages/" + sondageId + "/reponses").andExpect(status().isNotFound());
        adminActif("b@test.com");
        getAvec(jeton("b@test.com"), "/api/sondages/" + sondageId + "/lien").andExpect(status().isNotFound());
        mockMvc.perform(get("/api/sondages/" + sondageId + "/collecte")).andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- utilitaires

    /** Une réponse valide par question, indexée par libellé. */
    private static Map<String, Object> reponsesValides() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("Région", "kaolack");
        r.put("Cultures", List.of("arachide", "mil"));
        r.put("Engrais ?", true);
        r.put("Surface (ha)", 12.5);
        r.put("Semis", "2026-07-14");
        r.put("Village", "Ndiaffate");
        r.put("Difficultés", "Manque d'eau en août.");
        r.put("Satisfaction", 4);
        r.put("Priorités", List.of("engrais", "semences", "credit"));
        r.put("Services", Map.of("Vulgarisation", "Bon", "Marché", "Moyen"));
        return r;
    }

    private List<Map<String, Object>> liste(Map<String, Object> parLibelle) {
        List<Map<String, Object>> liste = new ArrayList<>();
        parLibelle.forEach((libelle, valeur) -> liste.add(Map.of("questionId", questions.get(libelle), "valeur", valeur)));
        return liste;
    }

    private ResultActions repondre(String appareil, Map<String, Object> parLibelle) throws Exception {
        Map<String, Object> corps = new HashMap<>();
        corps.put("reponses", liste(parLibelle));
        if (appareil != null) {
            corps.put("jetonRepondant", appareil);
        }
        return envoyer(corps);
    }

    private ResultActions envoyer(Object corps) throws Exception {
        return mockMvc.perform(post("/api/public/sondages/" + lien + "/reponses")
                .contentType(MediaType.APPLICATION_JSON).content(json(corps)));
    }

    private long creerSondageDixTypes(Map<String, Object> creation) throws Exception {
        long id = lire(postAvec(jetonAdmin, "/api/sondages", creation).andExpect(status().isCreated())).get("id").asLong();
        long section = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/sections", Map.of("titre", "Section")))
                .at("/sections/0/id").asLong();
        String url = "/api/sondages/" + id + "/sections/" + section + "/questions";
        UnaryOperator<Map<String, Object>> facultative = q -> {
            q.put("obligatoire", false);
            return q;
        };
        for (Map<String, Object> question : List.of(
                SondageIntegrationTest.question("Région", "CHOIX_UNIQUE", null, "Kaolack", "Fatick"),
                SondageIntegrationTest.question("Cultures", "CHOIX_MULTIPLE", null, "Arachide", "Mil", "Maïs"),
                SondageIntegrationTest.question("Engrais ?", "OUI_NON", null),
                SondageIntegrationTest.question("Surface (ha)", "NUMERIQUE", Map.of("min", 0, "max", 500, "decimales", 1)),
                facultative.apply(SondageIntegrationTest.question("Semis", "DATE",
                        Map.of("dateMin", "2026-05-01", "dateMax", "2026-09-30"))),
                facultative.apply(SondageIntegrationTest.question("Village", "TEXTE_COURT", Map.of("longueurMax", 100))),
                SondageIntegrationTest.question("Difficultés", "TEXTE_LONG", null),
                SondageIntegrationTest.question("Satisfaction", "ECHELLE", Map.of("max", 10)),
                SondageIntegrationTest.question("Priorités", "CLASSEMENT", null, "Semences", "Engrais", "Crédit"),
                SondageIntegrationTest.question("Services", "MATRICE",
                        Map.of("lignes", List.of("Vulgarisation", "Marché"), "colonnes", List.of("Mauvais", "Moyen", "Bon"))))) {
            postAvec(jetonAdmin, url, question).andExpect(status().isCreated());
        }
        return id;
    }

    private Map<String, Long> idsDesQuestions(long id) throws Exception {
        Map<String, Long> ids = new HashMap<>();
        lire(getAvec(jetonAdmin, "/api/sondages/" + id)).at("/sections/0/questions")
                .forEach(q -> ids.put(q.get("libelle").asText(), q.get("id").asLong()));
        return ids;
    }

    private String jetonPublic(long id) throws Exception {
        return lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/lien").andExpect(status().isOk())).get("jeton").asText();
    }

    private long compter(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
