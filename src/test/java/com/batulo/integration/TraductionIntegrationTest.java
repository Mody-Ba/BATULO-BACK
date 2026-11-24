package com.batulo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Traduction du contenu des sondages (fr, en, ar) : langues du brouillon, traductions côte à côte, complétude,
 * publication refusée puis acceptée, formulaire public et aperçu dans chaque langue (avec secours), langue de la
 * réponse, statistiques communes et filtrées, exports (colonne Langue, libellés traduits), copies.
 */
class TraductionIntegrationTest extends PostgresIntegrationTestBase {

    private static final Function<String, String> EN = source -> "EN " + source;
    private static final Function<String, String> AR = source -> "عربي " + source;

    private String jetonAdmin;

    @BeforeEach
    void preparer() throws Exception {
        adminActif("awa@test.com");
        jetonAdmin = jeton("awa@test.com");
    }

    // ------------------------------------------------------------------ Langues, traductions, complétude

    @Test
    void languesTraductionsCompletudeEtPublication() throws Exception {
        long id = brouillon("Satisfaction");
        // Par défaut : français, sans langue supplémentaire.
        getAvec(jetonAdmin, "/api/sondages/" + id)
                .andExpect(jsonPath("$.languePrincipale").value("fr"))
                .andExpect(jsonPath("$.languesSupplementaires.length()").value(0));

        // Langues invalides.
        langues(id, "fr", List.of("fr")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LANGUE_INVALIDE"));
        langues(id, "fr", List.of("en", "en")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LANGUE_INVALIDE"));
        langues(id, "de", List.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LANGUE_INVALIDE"));
        langues(id, "fr", List.of("en", "ar")).andExpect(status().isOk())
                .andExpect(jsonPath("$.languePrincipale").value("fr"))
                .andExpect(jsonPath("$.languesSupplementaires[0]").value("en"))
                .andExpect(jsonPath("$.languesSupplementaires[1]").value("ar"));

        // Côte à côte : textes non vides de la langue principale, dans l'ordre du formulaire.
        JsonNode en = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/en").andExpect(status().isOk()));
        assertThat(en.get("langue").asText()).isEqualTo("en");
        assertThat(en.get("languePrincipale").asText()).isEqualTo("fr");
        assertThat(en.get("traduits").asInt()).isZero();
        List<String> cles = new ArrayList<>();
        en.get("textes").forEach(t -> cles.add(t.get("cle").asText()));
        long questionMatrice = questionId(id, "Services");
        assertThat(cles).startsWith("sondage.titre", "sondage.description")
                .contains("question." + questionMatrice + ".ligne.1", "question." + questionMatrice + ".colonne.2")
                .doesNotContain("sondage.objetEnquete", "sondage.texteConsentement");
        int total = en.get("total").asInt();
        assertThat(total).isEqualTo(cles.size());
        assertThat(en.at("/textes/0/source").asText()).isEqualTo("Satisfaction");
        assertThat(en.at("/textes/0/traduction").isNull()).isTrue();

        getAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/de").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LANGUE_INVALIDE"));
        getAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/fr").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LANGUE_INVALIDE"));

        // Enregistrement en une fois ; clé inconnue ou en double : rien n'est enregistré.
        putAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/en",
                Map.of("textes", List.of(Map.of("cle", "sondage.titre", "traduction", "X"),
                        Map.of("cle", "question.999999.libelle", "traduction", "Y"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TRADUCTION_INVALIDE"));
        putAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/en",
                Map.of("textes", List.of(Map.of("cle", "sondage.titre", "traduction", "X"),
                        Map.of("cle", "sondage.titre", "traduction", "Y"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TRADUCTION_INVALIDE"));
        getAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/en").andExpect(jsonPath("$.traduits").value(0));

        traduire(id, "en", EN).andExpect(status().isOk())
                .andExpect(jsonPath("$.traduits").value(total))
                .andExpect(jsonPath("$.complet").value(true))
                .andExpect(jsonPath("$.textes[0].traduction").value("EN Satisfaction"));

        // Complétude : en complet, ar vide.
        JsonNode completude = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/traductions").andExpect(status().isOk()));
        assertThat(completude).hasSize(2);
        assertThat(completude.at("/0/langue").asText()).isEqualTo("en");
        assertThat(completude.at("/0/complet").asBoolean()).isTrue();
        assertThat(completude.at("/0/manquants")).isEmpty();
        assertThat(completude.at("/1/langue").asText()).isEqualTo("ar");
        assertThat(completude.at("/1/traduits").asInt()).isZero();
        assertThat(completude.at("/1/total").asInt()).isEqualTo(total);
        assertThat(completude.at("/1/manquants/0/cle").asText()).isEqualTo("sondage.titre");
        assertThat(completude.at("/1/manquants/0/source").asText()).isEqualTo("Satisfaction");

        // Publication refusée tant que l'arabe est incomplet, avec la liste des manquants.
        JsonNode refus = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRADUCTIONS_INCOMPLETES")));
        assertThat(refus.get("manquants")).hasSize(total);
        assertThat(refus.at("/manquants/0/langue").asText()).isEqualTo("ar");
        assertThat(refus.at("/manquants/0/cle").asText()).isEqualTo("sondage.titre");

        // Une traduction effacée ("") redevient manquante.
        traduire(id, "ar", AR).andExpect(status().isOk()).andExpect(jsonPath("$.complet").value(true));
        putAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/ar",
                Map.of("textes", List.of(Map.of("cle", "sondage.titre", "traduction", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traduits").value(total - 1));
        postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.manquants.length()").value(1))
                .andExpect(jsonPath("$.manquants[0].cle").value("sondage.titre"));
        traduire(id, "ar", AR).andExpect(status().isOk());
        publier(id);

        // Publié : traductions et langues figées.
        traduire(id, "en", s -> "Autre").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SONDAGE_PUBLIE"));
        langues(id, "fr", List.of()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SONDAGE_PUBLIE"));
        // La lecture reste possible.
        getAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/ar").andExpect(status().isOk())
                .andExpect(jsonPath("$.complet").value(true));
    }

    @Test
    void texteModifieEtChangementDeLanguePrincipale() throws Exception {
        long id = brouillon("Titre");
        langues(id, "fr", List.of("en")).andExpect(status().isOk());
        traduire(id, "en", EN).andExpect(status().isOk()).andExpect(jsonPath("$.complet").value(true));

        // Un texte principal modifié perd sa traduction ; une ligne de matrice renommée aussi.
        putAvec(jetonAdmin, "/api/sondages/" + id, Map.of("titre", "Nouveau titre", "description", "Description"))
                .andExpect(status().isOk());
        long matrice = questionId(id, "Services");
        putAvec(jetonAdmin, "/api/sondages/" + id + "/questions/" + matrice, matrice(List.of("Eau potable", "Santé")))
                .andExpect(status().isOk());
        JsonNode completude = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/traductions"));
        List<String> manquants = new ArrayList<>();
        completude.at("/0/manquants").forEach(m -> manquants.add(m.get("cle").asText()));
        assertThat(manquants).containsExactly("sondage.titre", "sondage.description", "question." + matrice + ".ligne.1");

        // Changer la langue principale ne change que l'étiquette : les textes restent ceux saisis.
        langues(id, "en", List.of("fr")).andExpect(status().isOk())
                .andExpect(jsonPath("$.languePrincipale").value("en"))
                .andExpect(jsonPath("$.titre").value("Nouveau titre"));
        getAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/fr").andExpect(jsonPath("$.traduits").value(0));
        getAvec(jetonAdmin, "/api/sondages/" + id + "/apercu?langue=en")
                .andExpect(jsonPath("$.langue").value("en"))
                .andExpect(jsonPath("$.titre").value("Nouveau titre"))
                .andExpect(jsonPath("$.description").value("Description"));
    }

    // ------------------------------------------------------------------ Formulaire public et aperçu

    @Test
    void formulairePublicDansChaqueLangueAvecSecours() throws Exception {
        long id = brouillon("Satisfaction");
        langues(id, "fr", List.of("en", "ar")).andExpect(status().isOk());
        traduire(id, "en", EN).andExpect(status().isOk());
        traduire(id, "ar", AR).andExpect(status().isOk());
        // Aperçu d'un brouillon incomplet : un texte non traduit revient à la langue principale.
        putAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/en",
                Map.of("textes", List.of(Map.of("cle", "sondage.description", "traduction", ""))))
                .andExpect(status().isOk());
        getAvec(jetonAdmin, "/api/sondages/" + id + "/apercu?langue=en")
                .andExpect(jsonPath("$.langue").value("en"))
                .andExpect(jsonPath("$.titre").value("EN Satisfaction"))
                .andExpect(jsonPath("$.description").value("Avis des clients"));
        traduire(id, "en", EN).andExpect(status().isOk());
        publier(id);
        String lien = jetonPublic(id);

        // Français (défaut).
        JsonNode fr = lire(mockMvc.perform(get("/api/public/sondages/" + lien)).andExpect(status().isOk()));
        assertThat(fr.get("langue").asText()).isEqualTo("fr");
        assertThat(fr.get("languePrincipale").asText()).isEqualTo("fr");
        assertThat(fr.get("languesDisponibles").toString()).isEqualTo("[\"fr\",\"en\",\"ar\"]");
        assertThat(fr.get("titre").asText()).isEqualTo("Satisfaction");
        JsonNode matriceFr = question(fr, "Services");
        assertThat(matriceFr.at("/libellesMatrice/lignes").toString()).isEqualTo("[\"Eau\",\"Santé\"]");

        // Anglais.
        JsonNode en = lire(mockMvc.perform(get("/api/public/sondages/" + lien + "?langue=en")).andExpect(status().isOk()));
        assertThat(en.get("langue").asText()).isEqualTo("en");
        assertThat(en.get("titre").asText()).isEqualTo("EN Satisfaction");
        assertThat(en.at("/sections/0/titre").asText()).isEqualTo("EN Profil");
        JsonNode sexe = question(en, "EN Sexe");
        assertThat(sexe.at("/options/0/libelle").asText()).isEqualTo("EN Homme");
        assertThat(sexe.at("/options/0/valeur").asText()).isEqualTo("homme");
        JsonNode echelle = question(en, "EN Note");
        assertThat(echelle.at("/configuration/libelleMin").asText()).isEqualTo("EN Mauvais");
        assertThat(echelle.at("/configuration/max").asInt()).isEqualTo(5);
        JsonNode matrice = question(en, "EN Services");
        // Valeurs stables inchangées ; libellés traduits à côté.
        assertThat(matrice.at("/configuration/lignes").toString()).isEqualTo("[\"Eau\",\"Santé\"]");
        assertThat(matrice.at("/libellesMatrice/lignes").toString()).isEqualTo("[\"EN Eau\",\"EN Santé\"]");
        assertThat(matrice.at("/libellesMatrice/colonnes").toString()).isEqualTo("[\"EN Bon\",\"EN Mauvais\"]");

        // Arabe.
        mockMvc.perform(get("/api/public/sondages/" + lien + "?langue=AR")).andExpect(status().isOk())
                .andExpect(jsonPath("$.langue").value("ar"))
                .andExpect(jsonPath("$.titre").value("عربي Satisfaction"))
                .andExpect(jsonPath("$.sections[0].questions[0].options[1].libelle").value("عربي Femme"))
                .andExpect(jsonPath("$.sections[0].questions[0].options[1].valeur").value("femme"));

        // Langue non proposée ou inconnue : langue principale.
        mockMvc.perform(get("/api/public/sondages/" + lien + "?langue=xx")).andExpect(status().isOk())
                .andExpect(jsonPath("$.langue").value("fr"))
                .andExpect(jsonPath("$.titre").value("Satisfaction"));
    }

    // ------------------------------------------------------------------ Réponses, statistiques, exports

    @Test
    void reponseEnArabeCompteeAvecLesAutresEtExports() throws Exception {
        long id = brouillon("Satisfaction");
        langues(id, "fr", List.of("ar")).andExpect(status().isOk());
        traduire(id, "ar", AR).andExpect(status().isOk());
        publier(id);
        String lien = jetonPublic(id);
        long sexe = questionId(id, "Sexe");
        long note = questionId(id, "Note");
        long matrice = questionId(id, "Services");

        // Valeurs stables quelle que soit la langue : codes d'options et textes principaux de la matrice.
        envoyer(lien, null, sexe, note, matrice, "homme").andExpect(status().isCreated());
        envoyer(lien, "ar", sexe, note, matrice, "homme").andExpect(status().isCreated());
        envoyer(lien, "ar", sexe, note, matrice, "femme").andExpect(status().isCreated());
        envoyer(lien, "en", sexe, note, matrice, "homme").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LANGUE_INVALIDE"));
        envoyer(lien, "xx", sexe, note, matrice, "homme").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LANGUE_INVALIDE"));
        assertThat(jdbcTemplate.queryForList("select langue from reponse where sondage_id = ? order by id",
                String.class, id)).containsExactly("fr", "ar", "ar");
        getAvec(jetonAdmin, "/api/sondages/" + id + "/reponses")
                .andExpect(jsonPath("$.contenu[0].langue").exists());

        // Statistiques communes à toutes les langues, libellés en langue principale par défaut.
        JsonNode stats = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques").andExpect(status().isOk()));
        assertThat(stats.get("langue").asText()).isEqualTo("fr");
        assertThat(stats.at("/resume/nombreReponses").asLong()).isEqualTo(3);
        assertThat(stats.get("repartitionLangues").toString())
                .isEqualTo("[{\"langue\":\"fr\",\"nombre\":1},{\"langue\":\"ar\",\"nombre\":2}]");
        JsonNode sexeStat = stats.at("/questions/0");
        assertThat(sexeStat.get("libelle").asText()).isEqualTo("Sexe");
        assertThat(sexeStat.at("/options/0/nombre").asLong()).isEqualTo(2);
        assertThat(sexeStat.at("/options/1/nombre").asLong()).isEqualTo(1);

        // Libellés en arabe, valeurs inchangées.
        JsonNode ar = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques?langue=ar").andExpect(status().isOk()));
        assertThat(ar.get("langue").asText()).isEqualTo("ar");
        assertThat(ar.get("titre").asText()).isEqualTo("عربي Satisfaction");
        assertThat(ar.at("/questions/0/libelle").asText()).isEqualTo("عربي Sexe");
        assertThat(ar.at("/questions/0/section").asText()).isEqualTo("عربي Profil");
        assertThat(ar.at("/questions/0/options/0/libelle").asText()).isEqualTo("عربي Homme");
        assertThat(ar.at("/questions/0/options/0/valeur").asText()).isEqualTo("homme");
        JsonNode matriceStat = ar.at("/questions/2/matrice");
        assertThat(matriceStat.at("/lignes/0/ligne").asText()).isEqualTo("Eau");
        assertThat(matriceStat.at("/lignes/0/libelle").asText()).isEqualTo("عربي Eau");
        assertThat(matriceStat.get("libellesColonnes").toString()).isEqualTo("[\"عربي Bon\",\"عربي Mauvais\"]");
        assertThat(matriceStat.at("/lignes/0/cellules/0/nombre").asLong()).isEqualTo(3);

        // Filtre par langue de réponse (OU entre langues).
        getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques?langueReponse=ar")
                .andExpect(jsonPath("$.resume.nombreReponses").value(2))
                .andExpect(jsonPath("$.languesReponse[0]").value("ar"))
                .andExpect(jsonPath("$.questions[0].options[1].nombre").value(1));
        getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques?langueReponse=fr&langueReponse=ar")
                .andExpect(jsonPath("$.resume.nombreReponses").value(3));
        getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques?langueReponse=en")
                .andExpect(jsonPath("$.resume.nombreReponses").value(0));
        getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques?langue=de").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LANGUE_INVALIDE"));

        // Export CSV : colonne Langue après Version ; libellés de la langue demandée ; filtre par langue.
        List<String[]> lignes = csv(id, "");
        assertThat(lignes.get(0)).startsWith("Id", "Date", "Version", "Langue", "Sexe");
        assertThat(lignes.subList(1, lignes.size()).stream().map(l -> l[3]).toList()).containsExactly("fr", "ar", "ar");
        assertThat(lignes.get(1)[4]).isEqualTo("Homme");
        List<String[]> enArabe = csv(id, "&langue=ar&langueReponse=ar");
        assertThat(enArabe).hasSize(3);
        assertThat(enArabe.get(0)).contains("عربي Sexe", "عربي Services – عربي Eau");
        assertThat(enArabe.get(2)[4]).isEqualTo("عربي Femme");
        assertThat(enArabe.get(1)).contains("عربي Bon");
        // CODES : valeurs stables (texte principal de la colonne de matrice).
        assertThat(csv(id, "&valeurs=CODES&langue=ar").get(1)).contains("homme", "Bon");

        // XLSX : même colonne Langue.
        byte[] xlsx = getAvec(jetonAdmin, "/api/sondages/" + id + "/export/reponses?format=XLSX&langue=ar")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook classeur = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            var feuille = classeur.getSheetAt(0);
            assertThat(feuille.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Langue");
            assertThat(feuille.getRow(2).getCell(3).getStringCellValue()).isEqualTo("ar");
        }

        // Rapports PDF en arabe (police Noto Sans Arabic) et XLSX.
        byte[] pdf = getAvec(jetonAdmin, "/api/sondages/" + id + "/export/statistiques?format=PDF&langue=ar")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF");
        PdfReader lecteur = new PdfReader(pdf);
        assertThat(lecteur.getNumberOfPages()).isGreaterThan(1);
        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("NotoSansArabic");
        assertThat(new PdfTextExtractor(lecteur).getTextFromPage(1)).contains("Rapport de statistiques");
        getAvec(jetonAdmin, "/api/sondages/" + id + "/export/statistiques?format=XLSX&langue=ar&langueReponse=ar")
                .andExpect(status().isOk());
        String cleSexe = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques")).at("/questions/0/cle").asText();
        String cleNote = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques")).at("/questions/1/cle").asText();
        putAvec(jetonAdmin, "/api/sondages/" + id + "/questions/" + sexe + "/critere", Map.of("estCritere", true))
                .andExpect(status().isOk());
        getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques/croisement?question=" + cleNote + "&par=" + cleSexe
                + "&langue=ar").andExpect(status().isOk())
                .andExpect(jsonPath("$.par.libelle").value("عربي Sexe"))
                .andExpect(jsonPath("$.colonnes[0].libelle").value("عربي Homme"));
        getAvec(jetonAdmin, "/api/sondages/" + id + "/export/croisement?format=PDF&question=" + cleNote + "&par="
                + cleSexe + "&langue=ar").andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ Copies

    @Test
    void languesEtTraductionsCopieesDansLesVersionsEtCopies() throws Exception {
        long id = brouillon("Source");
        langues(id, "fr", List.of("en", "ar")).andExpect(status().isOk());
        traduire(id, "en", EN).andExpect(status().isOk());
        traduire(id, "ar", AR).andExpect(status().isOk());
        publier(id);

        JsonNode version = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/nouvelle-version", Map.of())
                .andExpect(status().isCreated()));
        long v2 = version.get("id").asLong();
        assertThat(version.get("languePrincipale").asText()).isEqualTo("fr");
        assertThat(version.get("languesSupplementaires").toString()).isEqualTo("[\"en\",\"ar\"]");
        JsonNode completude = lire(getAvec(jetonAdmin, "/api/sondages/" + v2 + "/traductions"));
        assertThat(completude.at("/0/complet").asBoolean()).isTrue();
        assertThat(completude.at("/1/complet").asBoolean()).isTrue();
        getAvec(jetonAdmin, "/api/sondages/" + v2 + "/apercu?langue=ar")
                .andExpect(jsonPath("$.sections[0].questions[0].libelle").value("عربي Sexe"));
        // La version se publie telle quelle, et ses traductions se modifient sans toucher la source.
        traduire(v2, "en", s -> "V2 " + s).andExpect(status().isOk());
        getAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/en")
                .andExpect(jsonPath("$.textes[0].traduction").value("EN Source"));
        publier(v2);

        // Duplication : titre changé (« Copie de ... ») donc sa traduction est retirée, le reste est copié.
        long copie = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/dupliquer", Map.of())
                .andExpect(status().isCreated())).get("id").asLong();
        JsonNode completudeCopie = lire(getAvec(jetonAdmin, "/api/sondages/" + copie + "/traductions"));
        assertThat(completudeCopie.at("/0/manquants").size()).isEqualTo(1);
        assertThat(completudeCopie.at("/0/manquants/0/cle").asText()).isEqualTo("sondage.titre");

        // Réutilisation avec le même titre : tout est copié.
        long reutilise = lire(postAvec(jetonAdmin, "/api/sondages", Map.of("titre", "Source", "sondageSourceId", id))
                .andExpect(status().isCreated())).get("id").asLong();
        getAvec(jetonAdmin, "/api/sondages/" + reutilise + "/traductions")
                .andExpect(jsonPath("$[0].complet").value(true))
                .andExpect(jsonPath("$[1].complet").value(true));
    }

    // ------------------------------------------------------------------ Outils

    /**
     * Brouillon en collecte terrain : section « Profil » (description), CHOIX_UNIQUE « Sexe » (Homme, Femme),
     * ECHELLE « Note » (libellés Mauvais / Excellent), MATRICE « Services » (Eau, Santé × Bon, Mauvais).
     */
    private long brouillon(String titre) throws Exception {
        long id = lire(postAvec(jetonAdmin, "/api/sondages", Map.of("titre", titre, "description", "Avis des clients",
                "reponseUnique", false)).andExpect(status().isCreated())).get("id").asLong();
        long section = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/sections",
                Map.of("titre", "Profil", "description", "Quelques questions"))).at("/sections/0/id").asLong();
        String questions = "/api/sondages/" + id + "/sections/" + section + "/questions";
        postAvec(jetonAdmin, questions, SondageIntegrationTest.question("Sexe", "CHOIX_UNIQUE", null, "Homme", "Femme"))
                .andExpect(status().isCreated());
        postAvec(jetonAdmin, questions, SondageIntegrationTest.question("Note", "ECHELLE",
                Map.of("min", 1, "max", 5, "libelleMin", "Mauvais", "libelleMax", "Excellent")))
                .andExpect(status().isCreated());
        postAvec(jetonAdmin, questions, matrice(List.of("Eau", "Santé"))).andExpect(status().isCreated());
        return id;
    }

    private static Map<String, Object> matrice(List<String> lignes) {
        return SondageIntegrationTest.question("Services", "MATRICE",
                Map.of("lignes", lignes, "colonnes", List.of("Bon", "Mauvais")));
    }

    private ResultActions langues(long id, String principale, List<String> supplementaires) throws Exception {
        Map<String, Object> corps = new HashMap<>();
        corps.put("languePrincipale", principale);
        corps.put("languesSupplementaires", supplementaires);
        return putAvec(jetonAdmin, "/api/sondages/" + id + "/langues", corps);
    }

    /** Traduit tous les textes du sondage dans cette langue. */
    private ResultActions traduire(long id, String langue, Function<String, String> traduction) throws Exception {
        JsonNode textes = lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/" + langue)).get("textes");
        List<Map<String, Object>> liste = new ArrayList<>();
        textes.forEach(t -> liste.add(Map.of("cle", t.get("cle").asText(),
                "traduction", traduction.apply(t.get("source").asText()))));
        return putAvec(jetonAdmin, "/api/sondages/" + id + "/traductions/" + langue, Map.of("textes", liste));
    }

    private void publier(long id) throws Exception {
        postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
    }

    private String jetonPublic(long id) throws Exception {
        return lire(getAvec(jetonAdmin, "/api/sondages/" + id + "/lien").andExpect(status().isOk()))
                .get("jeton").asText();
    }

    private long questionId(long sondageId, String libelle) throws Exception {
        for (JsonNode q : lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId)).at("/sections/0/questions")) {
            if (q.get("libelle").asText().equals(libelle)) {
                return q.get("id").asLong();
            }
        }
        throw new AssertionError("Question introuvable : " + libelle);
    }

    private static JsonNode question(JsonNode formulaire, String libelle) {
        for (JsonNode q : formulaire.at("/sections/0/questions")) {
            if (q.get("libelle").asText().equals(libelle)) {
                return q;
            }
        }
        throw new AssertionError("Question introuvable : " + libelle);
    }

    private ResultActions envoyer(String lien, String langue, long sexe, long note, long matrice, String valeurSexe)
            throws Exception {
        Map<String, Object> corps = new LinkedHashMap<>();
        if (langue != null) {
            corps.put("langue", langue);
        }
        corps.put("reponses", List.of(
                Map.of("questionId", sexe, "valeur", valeurSexe),
                Map.of("questionId", note, "valeur", 4),
                Map.of("questionId", matrice, "valeur", Map.of("Eau", "Bon", "Santé", "Mauvais"))));
        return mockMvc.perform(post("/api/public/sondages/" + lien + "/reponses")
                .contentType(MediaType.APPLICATION_JSON).content(json(corps)));
    }

    /** CSV par défaut (« ; »), sans le BOM. */
    private List<String[]> csv(long sondageId, String parametres) throws Exception {
        byte[] octets = getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/export/reponses?format=CSV" + parametres)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        String texte = new String(octets, 3, octets.length - 3, StandardCharsets.UTF_8);
        List<String[]> lignes = new ArrayList<>();
        for (String ligne : texte.split("\r\n")) {
            lignes.add(ligne.split(";", -1));
        }
        return lignes;
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
