package com.batulo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Page d'accueil configurable (objet, confidentialité, consentement) : configuration dans le brouillon, copies,
 * formulaire public, consentement exigé à l'envoi, date du consentement dans le détail et les exports.
 */
class PageAccueilIntegrationTest extends PostgresIntegrationTestBase {

    private static final String OBJET = "Mieux connaître les besoins des producteurs.";
    private static final String CONFIDENTIALITE = "Vos réponses sont anonymes.";
    private static final String CASE = "J'accepte de participer à cette enquête.";

    private String jetonAdmin;

    @BeforeEach
    void preparer() throws Exception {
        adminActif("awa@test.com");
        jetonAdmin = jeton("awa@test.com");
    }

    // ------------------------------------------------------------------ Configuration

    @Test
    void configurationDansLeBrouillon() throws Exception {
        long id = brouillon("Accueil");
        // Par défaut : pas de page d'accueil, pas de consentement.
        getAvec(jetonAdmin, "/api/sondages/" + id)
                .andExpect(jsonPath("$.consentementRequis").value(false))
                .andExpect(jsonPath("$.objetEnquete").doesNotExist());

        modifier(id, pageAccueil(OBJET, CONFIDENTIALITE, true, CASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objetEnquete").value(OBJET))
                .andExpect(jsonPath("$.texteConfidentialite").value(CONFIDENTIALITE))
                .andExpect(jsonPath("$.consentementRequis").value(true))
                .andExpect(jsonPath("$.texteConsentement").value(CASE));

        // Champs absents : inchangés (un front qui ne les connaît pas ne les efface pas).
        modifier(id, Map.of("titre", "Nouveau titre"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.titre").value("Nouveau titre"))
                .andExpect(jsonPath("$.objetEnquete").value(OBJET))
                .andExpect(jsonPath("$.consentementRequis").value(true))
                .andExpect(jsonPath("$.texteConsentement").value(CASE));

        // "" efface un texte ; on ne peut pas effacer celui de la case tant que le consentement est exigé.
        modifier(id, Map.of("titre", "T", "objetEnquete", "", "texteConfidentialite", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objetEnquete").doesNotExist())
                .andExpect(jsonPath("$.texteConfidentialite").doesNotExist());
        modifier(id, Map.of("titre", "T", "texteConsentement", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEXTE_CONSENTEMENT_REQUIS"));
        getAvec(jetonAdmin, "/api/sondages/" + id).andExpect(jsonPath("$.texteConsentement").value(CASE))
                .andExpect(jsonPath("$.titre").value("T"));

        // Sans exigence, le texte de la case peut être vidé ; l'exiger sans texte est refusé.
        modifier(id, Map.of("titre", "T", "consentementRequis", false, "texteConsentement", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.consentementRequis").value(false))
                .andExpect(jsonPath("$.texteConsentement").doesNotExist());
        modifier(id, Map.of("titre", "T", "consentementRequis", true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TEXTE_CONSENTEMENT_REQUIS"));
        modifier(id, Map.of("titre", "T", "texteConsentement", "x".repeat(2001))).andExpect(status().isBadRequest());

        // L'aperçu montre la page d'accueil.
        modifier(id, pageAccueil(OBJET, CONFIDENTIALITE, true, CASE)).andExpect(status().isOk());
        getAvec(jetonAdmin, "/api/sondages/" + id + "/apercu")
                .andExpect(jsonPath("$.objetEnquete").value(OBJET))
                .andExpect(jsonPath("$.consentementRequis").value(true))
                .andExpect(jsonPath("$.texteConsentement").value(CASE));

        // Publié : figé.
        publier(id);
        modifier(id, pageAccueil("Autre", null, false, null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SONDAGE_PUBLIE"));
    }

    @Test
    void recopieeDansLesVersionsDuplicationsEtReutilisations() throws Exception {
        long id = brouillon("Source");
        modifier(id, pageAccueil(OBJET, CONFIDENTIALITE, true, CASE)).andExpect(status().isOk());
        publier(id);

        JsonNode version = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/nouvelle-version", Map.of())
                .andExpect(status().isCreated()));
        JsonNode duplication = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/dupliquer", Map.of())
                .andExpect(status().isCreated()));
        JsonNode reutilisation = lire(postAvec(jetonAdmin, "/api/sondages",
                Map.of("titre", "Réutilisé", "sondageSourceId", id)).andExpect(status().isCreated()));
        for (JsonNode copie : List.of(version, duplication, reutilisation)) {
            assertThat(copie.get("objetEnquete").asText()).isEqualTo(OBJET);
            assertThat(copie.get("texteConfidentialite").asText()).isEqualTo(CONFIDENTIALITE);
            assertThat(copie.get("consentementRequis").asBoolean()).isTrue();
            assertThat(copie.get("texteConsentement").asText()).isEqualTo(CASE);
        }
        // La copie se modifie indépendamment de la source.
        modifier(version.get("id").asLong(), Map.of("titre", "v2", "objetEnquete", "Objet v2")).andExpect(status().isOk());
        getAvec(jetonAdmin, "/api/sondages/" + id).andExpect(jsonPath("$.objetEnquete").value(OBJET));
    }

    // ------------------------------------------------------------------ Formulaire public et envoi

    @Test
    void formulairePublicEtConsentementExige() throws Exception {
        long id = brouillon("Consentement");
        modifier(id, pageAccueil(OBJET, CONFIDENTIALITE, true, CASE)).andExpect(status().isOk());
        publier(id);
        String lien = jetonPublic(id);

        mockMvc.perform(get("/api/public/sondages/" + lien))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objetEnquete").value(OBJET))
                .andExpect(jsonPath("$.texteConfidentialite").value(CONFIDENTIALITE))
                .andExpect(jsonPath("$.consentementRequis").value(true))
                .andExpect(jsonPath("$.texteConsentement").value(CASE));

        long questionId = questionId(id);
        envoyer(lien, questionId, null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENTEMENT_REQUIS"));
        envoyer(lien, questionId, false).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENTEMENT_REQUIS"));
        assertThat(nombreReponses(id)).isZero();

        Instant avant = Instant.now();
        envoyer(lien, questionId, true).andExpect(status().isCreated());
        Instant apres = Instant.now();
        Timestamp date = jdbcTemplate.queryForObject("select date_consentement from reponse where sondage_id = ?",
                Timestamp.class, id);
        assertThat(date.toInstant()).isBetween(avant.minusSeconds(1), apres.plusSeconds(1));

        // Détail d'une réponse.
        JsonNode reponse = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/reponses")).at("/contenu/0");
        assertThat(Instant.parse(reponse.get("dateConsentement").asText())).isEqualTo(date.toInstant());
    }

    @Test
    void sansConsentementExigeLaCaseEstIgnoree() throws Exception {
        long id = brouillon("Sans consentement");
        publier(id);
        String lien = jetonPublic(id);
        mockMvc.perform(get("/api/public/sondages/" + lien))
                .andExpect(jsonPath("$.consentementRequis").value(false))
                .andExpect(jsonPath("$.objetEnquete").doesNotExist());

        envoyer(lien, questionId(id), null).andExpect(status().isCreated());
        envoyer(lien, questionId(id), true).andExpect(status().isCreated());
        assertThat(jdbcTemplate.queryForObject("select count(*) from reponse where sondage_id = ? "
                + "and date_consentement is not null", Long.class, id)).isZero();
        getAvec(jetonAdmin, "/api/sondages/" + id + "/reponses")
                .andExpect(jsonPath("$.contenu[0].dateConsentement").doesNotExist());

        // Export : pas de colonne Consentement, en-têtes inchangés.
        assertThat(entetesCsv(id, false)).containsExactly("Id", "Date", "Version", "Q");
    }

    @Test
    void consentementDansLesExportsDUneLignee() throws Exception {
        // v1 sans consentement, v2 avec : la lignée l'affiche, vide pour les réponses de la v1.
        long v1 = brouillon("Lignée");
        publier(v1);
        String lien = jetonPublic(v1);
        envoyer(lien, questionId(v1), null).andExpect(status().isCreated());

        long v2 = lire(postAvec(jetonAdmin, "/api/sondages/" + v1 + "/nouvelle-version", Map.of())
                .andExpect(status().isCreated())).get("id").asLong();
        modifier(v2, pageAccueil(OBJET, null, true, CASE)).andExpect(status().isOk());
        publier(v2);
        // Le même lien mène à la v2, qui exige le consentement.
        envoyer(lien, questionId(v2), null).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CONSENTEMENT_REQUIS"));
        envoyer(lien, questionId(v2), true).andExpect(status().isCreated());

        // v1 seule : pas de colonne ; v2 seule et lignée : colonne après Version.
        assertThat(entetesCsv(v1, false)).containsExactly("Id", "Date", "Version", "Q");
        assertThat(entetesCsv(v2, false)).containsExactly("Id", "Date", "Version", "Consentement", "Q");
        List<String[]> lignes = lignesCsv(v1, true);
        assertThat(lignes.get(0)).containsExactly("Id", "Date", "Version", "Consentement", "Q");
        Map<String, String[]> parVersion = new HashMap<>();
        lignes.subList(1, lignes.size()).forEach(l -> parVersion.put(l[2], l));
        assertThat(parVersion.get("1")[3]).isEmpty();
        assertThat(parVersion.get("2")[3]).matches("\\d{4}-\\d{2}-\\d{2}.*");
        assertThat(parVersion.get("2")[4]).isEqualTo("Oui");

        // XLSX : même colonne.
        MockHttpServletResponse xlsx = getAvec(jetonAdmin, "/api/sondages/" + v1
                + "/export/reponses?format=XLSX&lignee=true").andExpect(status().isOk()).andReturn().getResponse();
        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getContentAsByteArray()))) {
            var feuille = classeur.getSheetAt(0);
            assertThat(feuille.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Consentement");
            int avecDate = 0;
            for (Row ligne : feuille) {
                if (ligne.getRowNum() > 0 && ligne.getCell(3) != null
                        && ligne.getCell(3).getCellType() == CellType.NUMERIC) {
                    avecDate++;
                }
            }
            assertThat(avecDate).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ Outils

    private static Map<String, Object> pageAccueil(String objet, String confidentialite, boolean requis,
                                                   String texte) {
        Map<String, Object> corps = new HashMap<>();
        corps.put("titre", "Titre");
        corps.put("objetEnquete", objet);
        corps.put("texteConfidentialite", confidentialite);
        corps.put("consentementRequis", requis);
        corps.put("texteConsentement", texte);
        // reponseUnique false : plusieurs envois sans jeton d'appareil.
        corps.put("reponseUnique", false);
        return corps;
    }

    /** Brouillon en collecte terrain (plusieurs envois sans jeton) avec une question OUI_NON « Q ». */
    private long brouillon(String titre) throws Exception {
        long id = lire(postAvec(jetonAdmin, "/api/sondages", Map.of("titre", titre, "reponseUnique", false))
                .andExpect(status().isCreated())).get("id").asLong();
        long section = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/sections", Map.of("titre", "S")))
                .at("/sections/0/id").asLong();
        postAvec(jetonAdmin, "/api/sondages/" + id + "/sections/" + section + "/questions",
                SondageIntegrationTest.question("Q", "OUI_NON", null)).andExpect(status().isCreated());
        return id;
    }

    private ResultActions modifier(long id, Map<String, Object> corps) throws Exception {
        return putAvec(jetonAdmin, "/api/sondages/" + id, corps);
    }

    private void publier(long id) throws Exception {
        postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
    }

    private String jetonPublic(long id) throws Exception {
        return lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/lien").andExpect(status().isOk()))
                .get("jeton").asText();
    }

    private long questionId(long sondageId) throws Exception {
        return lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId)).at("/sections/0/questions/0/id").asLong();
    }

    private ResultActions envoyer(String lien, long questionId, Boolean consentement) throws Exception {
        Map<String, Object> corps = new HashMap<>();
        corps.put("reponses", List.of(Map.of("questionId", questionId, "valeur", true)));
        if (consentement != null) {
            corps.put("consentement", consentement);
        }
        return mockMvc.perform(post("/api/public/sondages/" + lien + "/reponses")
                .contentType(MediaType.APPLICATION_JSON).content(json(corps)));
    }

    private long nombreReponses(long sondageId) {
        return jdbcTemplate.queryForObject("select count(*) from reponse where sondage_id = ?", Long.class, sondageId);
    }

    private List<String> entetesCsv(long sondageId, boolean lignee) throws Exception {
        return List.of(lignesCsv(sondageId, lignee).get(0));
    }

    /** CSV par défaut (« ; »), sans le BOM ; cellules sans guillemets dans ces tests. */
    private List<String[]> lignesCsv(long sondageId, boolean lignee) throws Exception {
        byte[] octets = getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/export/reponses?format=CSV"
                + (lignee ? "&lignee=true" : "")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsByteArray();
        String csv = new String(octets, 3, octets.length - 3, StandardCharsets.UTF_8);
        List<String[]> lignes = new ArrayList<>();
        for (String ligne : csv.split("\r\n")) {
            lignes.add(ligne.split(";", -1));
        }
        return lignes;
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
