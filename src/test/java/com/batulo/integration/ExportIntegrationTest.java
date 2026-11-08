package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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

/** Étape 3.3 : exports CSV, XLSX et PDF, vérifiés sur un jeu de 3 réponses connu (et un gros volume). */
class ExportIntegrationTest extends PostgresIntegrationTestBase {

    private static final String ENTETES = "Id;Date;Version;Région;Cultures – Mil;Cultures – Arachide;Engrais ?;"
            + "Surface (ha);Semis;Remarque;Priorités – Semences;Priorités – Engrais;Services – Vulgarisation;"
            + "Services – Marché";

    private Utilisateur admin;
    private String jetonAdmin;
    private long sondageId;
    private Map<String, Long> ids;
    private Map<String, String> cles;

    @BeforeEach
    void jeuDeTroisReponses() throws Exception {
        admin = adminActif("awa@test.com");
        jetonAdmin = jeton("awa@test.com");
        long id = lire(postAvec(jetonAdmin, "/api/sondages", Map.of("titre", "Enquête agricole", "reponseUnique", false)))
                .get("id").asLong();
        long section = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/sections", Map.of("titre", "S")))
                .at("/sections/0/id").asLong();
        for (Map<String, Object> q : List.of(
                StatistiqueIntegrationTest.q("Région", "CHOIX_UNIQUE", true, null, "Kaolack", "Fatick"),
                StatistiqueIntegrationTest.q("Cultures", "CHOIX_MULTIPLE", false, null, "Mil", "Arachide"),
                StatistiqueIntegrationTest.q("Engrais ?", "OUI_NON", true, null),
                StatistiqueIntegrationTest.q("Surface (ha)", "NUMERIQUE", true, Map.of("min", 0, "max", 500, "decimales", 1)),
                StatistiqueIntegrationTest.q("Semis", "DATE", false, null),
                StatistiqueIntegrationTest.q("Remarque", "TEXTE_COURT", false, null),
                StatistiqueIntegrationTest.q("Priorités", "CLASSEMENT", true, null, "Semences", "Engrais"),
                StatistiqueIntegrationTest.q("Services", "MATRICE", true, Map.of("lignes", List.of("Vulgarisation", "Marché"),
                        "colonnes", List.of("Bon", "Moyen"))))) {
            postAvec(jetonAdmin, "/api/sondages/" + id + "/sections/" + section + "/questions", q)
                    .andExpect(status().isCreated());
        }
        postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
        sondageId = id;
        ids = new HashMap<>();
        lire(getAvec(jetonAdmin, "/api/sondages/" + id)).at("/sections/0/questions")
                .forEach(q -> ids.put(q.get("libelle").asText(), q.get("id").asLong()));
        putAvec(jetonAdmin, "/api/sondages/" + id + "/questions/" + ids.get("Région") + "/critere",
                Map.of("estCritere", true)).andExpect(status().isOk());

        repondre(reponse("kaolack", List.of("mil", "arachide"), true, 12.5, "2026-07-01", "=SOMME(A1)",
                List.of("engrais", "semences"), "Bon", "Moyen"));
        repondre(reponse("fatick", null, false, 3, null, "Texte; avec \"guillemets\"\net retour",
                List.of("semences", "engrais"), "Moyen", "Bon"));
        repondre(reponse("kaolack", List.of("arachide"), true, 0.5, "2026-07-15", null,
                List.of("engrais", "semences"), "Bon", "Bon"));
        cles = new HashMap<>();
        lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques")).get("questions")
                .forEach(q -> cles.put(q.get("libelle").asText(), q.get("cle").asText()));
    }

    @Test
    void csvExcelFrancophone() throws Exception {
        MockHttpServletResponse reponse = exporter("reponses?format=CSV");
        assertThat(reponse.getContentType()).isEqualTo("text/csv;charset=UTF-8");
        String disposition = reponse.getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertThat(disposition).matches("attachment; filename=\"enquete-agricole_v1_\\d{4}-\\d{2}-\\d{2}\\.csv\"; "
                + "filename\\*=UTF-8''enquete-agricole_v1_\\d{4}-\\d{2}-\\d{2}\\.csv");
        assertThat(reponse.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");

        byte[] octets = reponse.getContentAsByteArray();
        assertThat(new byte[]{octets[0], octets[1], octets[2]}).containsExactly(0xEF, 0xBB, 0xBF);
        String csv = new String(octets, 3, octets.length - 3, StandardCharsets.UTF_8);
        String[] lignes = csv.split("\r\n");
        assertThat(lignes[0]).isEqualTo(ENTETES);
        // Réponse 1 : formule neutralisée par une apostrophe, virgule décimale, 1/1 pour le choix multiple, rangs.
        assertThat(lignes[1]).matches("\\d+;\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2};1;Kaolack;1;1;Oui;12,5;2026-07-01;"
                + "'=SOMME\\(A1\\);2;1;Bon;Moyen");
        // Réponse 2 : choix multiple non répondu = cases vides ; texte entre guillemets (« ; », guillemets, retour).
        // Le retour à la ligne du texte (\n) reste dans les guillemets : la réponse 2 tient sur un seul enregistrement.
        assertThat(lignes[2]).matches("(?s)\\d+;[^;]+;1;Fatick;;;Non;3;;\"Texte; avec \"\"guillemets\"\"\net retour\";"
                + "1;2;Moyen;Bon");
        // Réponse 3 : arachide seule → 0/1.
        assertThat(csv).containsPattern("\\d+;[^;]+;1;Kaolack;0;1;Oui;0,5;2026-07-15;;2;1;Bon;Bon\r\n");
    }

    @Test
    void csvPourPythonEtModeCodes() throws Exception {
        String csv = texte(exporter("reponses?format=CSV&separateurDecimal=POINT&valeurs=CODES"));
        String[] lignes = csv.split("\r\n");
        assertThat(lignes[0]).isEqualTo(ENTETES.replace(';', ','));
        assertThat(lignes[1]).matches("\\d+,[^,]+,1,kaolack,1,1,1,12\\.5,2026-07-01,'=SOMME\\(A1\\),2,1,Bon,Moyen");
        assertThat(csv).containsPattern(",1,kaolack,0,1,1,0\\.5,2026-07-15,,2,1,Bon,Bon\r\n");
    }

    @Test
    void xlsxAvecCellulesTypees() throws Exception {
        MockHttpServletResponse reponse = exporter("reponses?format=XLSX");
        assertThat(reponse.getContentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(reponse.getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains(".xlsx");
        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(reponse.getContentAsByteArray()))) {
            Sheet feuille = classeur.getSheet("Réponses");
            assertThat(feuille.getLastRowNum()).isEqualTo(3);
            List<String> entetes = new ArrayList<>();
            feuille.getRow(0).forEach(c -> entetes.add(c.getStringCellValue()));
            assertThat(String.join(";", entetes)).isEqualTo(ENTETES);
            Row premiere = feuille.getRow(1);
            assertThat(premiere.getCell(3).getStringCellValue()).isEqualTo("Kaolack");
            assertThat(premiere.getCell(7).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(premiere.getCell(7).getNumericCellValue()).isEqualTo(12.5);
            assertThat(DateUtil.isCellDateFormatted(premiere.getCell(8))).isTrue();
            assertThat(premiere.getCell(8).getLocalDateTimeCellValue().toLocalDate()).hasToString("2026-07-01");
            // Le texte qui ressemble à une formule reste un texte.
            assertThat(premiere.getCell(9).getCellType()).isEqualTo(CellType.STRING);
            assertThat(premiere.getCell(9).getStringCellValue()).isEqualTo("=SOMME(A1)");
            assertThat(feuille.getRow(2).getCell(4)).isNull();
        }
    }

    @Test
    void filtresAppliquesALExport() throws Exception {
        String csv = texte(exporter("reponses?format=CSV&critere=" + cles.get("Région") + ":kaolack"));
        assertThat(csv.split("\r\n")).hasSize(3);
        assertThat(csv).doesNotContain("Fatick");
        getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/export/reponses?format=CSV&critere=" + cles.get("Cultures") + ":mil")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRITERE_INVALIDE"));
        getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/export/reponses?format=PDF")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FORMAT_INVALIDE"));
        getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/export/statistiques?format=CSV")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FORMAT_INVALIDE"));
    }

    @Test
    void grosVolumeEnFlux() throws Exception {
        long questionSurface = ids.get("Surface (ha)");
        jdbcTemplate.update("insert into reponse (sondage_id, lignee_id, date_soumission) "
                + "select ?, ?, now() from generate_series(1, 5000)", sondageId, sondageId);
        jdbcTemplate.update("insert into reponse_question (reponse_id, question_id, valeur_numerique) "
                + "select r.id, ?, r.id % 100 from reponse r where not exists "
                + "(select 1 from reponse_question rq where rq.reponse_id = r.id)", questionSurface);

        String csv = texte(exporter("reponses?format=CSV"));
        assertThat(csv.split("\r\n")).hasSize(5004);
        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(
                exporter("reponses?format=XLSX").getContentAsByteArray()))) {
            assertThat(classeur.getSheet("Réponses").getLastRowNum()).isEqualTo(5003);
        }
    }

    @Test
    void rapportPdfDesStatistiques() throws Exception {
        MockHttpServletResponse reponse = exporter("statistiques?format=PDF&critere=" + cles.get("Région") + ":kaolack");
        assertThat(reponse.getContentType()).isEqualTo("application/pdf");
        assertThat(reponse.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .containsPattern("enquete-agricole_v1_statistiques_\\d{4}-\\d{2}-\\d{2}\\.pdf");
        byte[] pdf = reponse.getContentAsByteArray();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        PdfReader lecteur = new PdfReader(pdf);
        PdfTextExtractor extracteur = new PdfTextExtractor(lecteur);
        String titre = extracteur.getTextFromPage(1);
        assertThat(titre).contains("Rapport de statistiques", "Enquête agricole", "Région : Kaolack", "Réponses");
        StringBuilder tout = new StringBuilder();
        for (int page = 1; page <= lecteur.getNumberOfPages(); page++) {
            tout.append(extracteur.getTextFromPage(page)).append('\n');
        }
        // 2 réponses retenues (Kaolack) : Engrais oui 2/2 = 100 %, petit effectif signalé.
        assertThat(tout.toString()).contains("Engrais ?", "100,0 %", "Petit effectif", "Priorités", "Page 1 / ");
        lecteur.close();
    }

    @Test
    void rapportXlsxDesStatistiquesEtCroisement() throws Exception {
        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(
                exporter("statistiques?format=XLSX").getContentAsByteArray()))) {
            assertThat(classeur.getSheetName(0)).isEqualTo("Résumé");
            assertThat(classeur.getNumberOfSheets()).isEqualTo(9);
            assertThat(classeur.getSheetName(1)).isEqualTo("Q1 Région");
            Sheet region = classeur.getSheet("Q1 Région");
            Row kaolack = ligneQuiCommencePar(region, "Kaolack");
            assertThat(kaolack.getCell(2).getNumericCellValue()).isEqualTo(2);
            assertThat(kaolack.getCell(3).getNumericCellValue()).isEqualTo(66.7);
        }

        String url = "croisement?question=" + cles.get("Engrais ?") + "&par=" + cles.get("Région");
        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(
                exporter(url + "&format=XLSX").getContentAsByteArray()))) {
            Sheet feuille = classeur.getSheet("Tableau croisé");
            Row oui = ligneQuiCommencePar(feuille, "Oui");
            // Kaolack : 2 oui sur 2 (100 %) ; Fatick : 0 oui.
            assertThat(oui.getCell(1).getNumericCellValue()).isEqualTo(2);
            assertThat(oui.getCell(2).getNumericCellValue()).isEqualTo(100.0);
            assertThat(oui.getCell(3).getNumericCellValue()).isZero();
        }
        MockHttpServletResponse pdf = exporter(url + "&format=PDF");
        assertThat(pdf.getHeader(HttpHeaders.CONTENT_DISPOSITION)).contains("_croisement_");
        PdfReader lecteur = new PdfReader(pdf.getContentAsByteArray());
        assertThat(new PdfTextExtractor(lecteur).getTextFromPage(1)).contains("Tableau croisé", "Kaolack", "Fatick");
        lecteur.close();
    }

    @Test
    void droits() throws Exception {
        gestionnaire("g@test.com", admin, true);
        getAvec(jeton("g@test.com"), "/api/sondages/" + sondageId + "/export/reponses?format=CSV")
                .andExpect(status().isNotFound());
        adminActif("b@test.com");
        getAvec(jeton("b@test.com"), "/api/sondages/" + sondageId + "/export/statistiques?format=PDF")
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/sondages/" + sondageId + "/export/reponses?format=CSV"))
                .andExpect(status().isUnauthorized());
        jdbcTemplate.update("update abonnement set date_expiration = ? where entreprise_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1))), admin.getEntreprise().getId());
        getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/export/reponses?format=CSV").andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- utilitaires

    private static Map<String, Object> reponse(String region, List<String> cultures, boolean engrais, double surface,
                                               String semis, String remarque, List<String> priorites,
                                               String vulgarisation, String marche) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("Région", region);
        r.put("Cultures", cultures);
        r.put("Engrais ?", engrais);
        r.put("Surface (ha)", surface);
        r.put("Semis", semis);
        r.put("Remarque", remarque);
        r.put("Priorités", priorites);
        r.put("Services", Map.of("Vulgarisation", vulgarisation, "Marché", marche));
        return r;
    }

    private void repondre(Map<String, Object> parLibelle) throws Exception {
        String jeton = lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/lien")).get("jeton").asText();
        List<Map<String, Object>> reponses = new ArrayList<>();
        parLibelle.forEach((libelle, valeur) -> {
            if (valeur != null) {
                reponses.add(Map.of("questionId", ids.get(libelle), "valeur", valeur));
            }
        });
        mockMvc.perform(post("/api/public/sondages/" + jeton + "/reponses").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("reponses", reponses)))).andExpect(status().isCreated());
    }

    private MockHttpServletResponse exporter(String suite) throws Exception {
        return getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/export/" + suite)
                .andExpect(status().isOk()).andReturn().getResponse();
    }

    private static String texte(MockHttpServletResponse reponse) {
        byte[] octets = reponse.getContentAsByteArray();
        return new String(octets, 3, octets.length - 3, StandardCharsets.UTF_8);
    }

    private static Row ligneQuiCommencePar(Sheet feuille, String debut) {
        for (Row ligne : feuille) {
            if (ligne.getCell(0) != null && ligne.getCell(0).getCellType() == CellType.STRING
                    && ligne.getCell(0).getStringCellValue().equals(debut)) {
                return ligne;
            }
        }
        throw new AssertionError("Ligne absente : " + debut);
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
