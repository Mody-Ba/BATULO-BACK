package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 4, étape 2 : personnalisation de l'entreprise (logo, couleur, nom affiché). */
class PersonnalisationIntegrationTest extends PostgresIntegrationTestBase {

    private static final String LOGO = "/api/admin/personnalisation/logo";
    private static final String REGLAGES = "/api/admin/personnalisation";
    private static final String LECTURE = "/api/entreprise/personnalisation";
    private static final Pattern IMAGE_PDF = Pattern.compile("/Subtype\\s*/Image");

    private Utilisateur admin;
    private String jetonAdmin;

    @BeforeEach
    void creerLAdmin() throws Exception {
        admin = adminActif("awa@test.com");
        jetonAdmin = jeton("awa@test.com");
    }

    // ---------------------------------------------------------------- Logo

    @Test
    void logoPngValideServiParLaRoutePublique() throws Exception {
        byte[] png = image("png", 300, 120);
        JsonNode reglages = lire(envoyerLogo(jetonAdmin, "logo.png", "image/png", png).andExpect(status().isOk()));
        assertThat(reglages.at("/logo/typeMime").asText()).isEqualTo("image/png");
        assertThat(reglages.at("/logo/largeur").asInt()).isEqualTo(300);
        assertThat(reglages.at("/logo/hauteur").asInt()).isEqualTo(120);
        assertThat(reglages.at("/logo/taille").asInt()).isEqualTo(png.length);
        String url = reglages.at("/logo/url").asText();
        assertThat(url).matches("http://localhost:8080/api/public/logos/[0-9a-f-]{36}");

        // Route publique, sans authentification : le contenu exact, avec les bons en-têtes.
        MockHttpServletResponse reponse = mockMvc.perform(get(chemin(url)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("max-age=31536000")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("immutable")))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'"))
                .andReturn().getResponse();
        assertThat(reponse.getContentAsByteArray()).isEqualTo(png);

        // En base, pas sur le disque.
        assertThat(jdbcTemplate.queryForObject("select octet_length(contenu) from entreprise_logo", Integer.class))
                .isEqualTo(png.length);
    }

    @Test
    void remplacementChangeLUrlEtSuppression() throws Exception {
        String ancienne = lire(envoyerLogo(jetonAdmin, "a.png", "image/png", image("png", 50, 50))).at("/logo/url").asText();
        // JPEG, même envoyé avec une extension et un Content-Type quelconques : le type vient des octets.
        JsonNode jpeg = lire(envoyerLogo(jetonAdmin, "logo.bin", "application/octet-stream", image("jpg", 80, 40))
                .andExpect(status().isOk()));
        String nouvelle = jpeg.at("/logo/url").asText();
        assertThat(jpeg.at("/logo/typeMime").asText()).isEqualTo("image/jpeg");
        assertThat(nouvelle).isNotEqualTo(ancienne);
        mockMvc.perform(get(chemin(ancienne))).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOGO_INTROUVABLE"));
        mockMvc.perform(get(chemin(nouvelle))).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from entreprise_logo", Long.class)).isEqualTo(1);

        deleteAvec(jetonAdmin, LOGO).andExpect(status().isOk()).andExpect(jsonPath("$.logo").isEmpty());
        mockMvc.perform(get(chemin(nouvelle))).andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("select count(*) from entreprise_logo", Long.class)).isZero();
        // Supprimer sans logo : rien à faire.
        deleteAvec(jetonAdmin, LOGO).andExpect(status().isOk());
    }

    @Test
    void formatsRefuses() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        envoyerLogo(jetonAdmin, "logo.svg", "image/svg+xml", svg).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOGO_FORMAT_INVALIDE"));
        // Faux PNG : un texte renommé, avec le Content-Type image/png.
        envoyerLogo(jetonAdmin, "logo.png", "image/png", "pas une image".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOGO_FORMAT_INVALIDE"));
        envoyerLogo(jetonAdmin, "logo.gif", "image/gif", image("gif", 20, 20)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOGO_FORMAT_INVALIDE"));
        // Signature PNG suivie de n'importe quoi.
        byte[] corrompu = new byte[200];
        System.arraycopy(image("png", 10, 10), 0, corrompu, 0, 8);
        envoyerLogo(jetonAdmin, "logo.png", "image/png", corrompu).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOGO_ILLISIBLE"));
        envoyerLogo(jetonAdmin, "large.png", "image/png", image("png", 2001, 10)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LOGO_DIMENSIONS"))
                .andExpect(jsonPath("$.message").value(containsString("2001 x 10")));
        envoyerLogo(jetonAdmin, "vide.png", "image/png", new byte[0]).andExpect(status().isBadRequest());
        // Sans le champ « fichier ».
        mockMvc.perform(multipart(HttpMethod.PUT, LOGO).file(new MockMultipartFile("autre", "a.png", "image/png",
                        image("png", 10, 10))).header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAdmin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FICHIER_MANQUANT"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from entreprise_logo", Long.class)).isZero();
    }

    @Test
    void fichierTropGros() throws Exception {
        byte[] gros = bruit(700, 700);
        assertThat(gros.length).isGreaterThan(1024 * 1024);
        envoyerLogo(jetonAdmin, "gros.png", "image/png", gros)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FICHIER_TROP_GROS"));
    }

    // ---------------------------------------------------------------- Droits

    @Test
    void gestionnaireLitMaisNeModifiePas() throws Exception {
        gestionnaire("g@test.com", admin, true);
        String jetonGestionnaire = jeton("g@test.com");
        putAvec(jetonAdmin, REGLAGES, Map.of("couleurPrincipale", "#1E6B52")).andExpect(status().isOk());

        getAvec(jetonGestionnaire, LECTURE).andExpect(status().isOk())
                .andExpect(jsonPath("$.couleurPrincipale").value("#1E6B52"));
        putAvec(jetonGestionnaire, REGLAGES, Map.of("couleurPrincipale", "#000000")).andExpect(status().isForbidden());
        envoyerLogo(jetonGestionnaire, "logo.png", "image/png", image("png", 10, 10)).andExpect(status().isForbidden());
        deleteAvec(jetonGestionnaire, LOGO).andExpect(status().isForbidden());
        getAvec(jetonAdmin, LECTURE).andExpect(jsonPath("$.couleurPrincipale").value("#1E6B52"));
    }

    @Test
    void chaqueEntrepriseNeVoitEtNeModifieQueLaSienne() throws Exception {
        envoyerLogo(jetonAdmin, "logo.png", "image/png", image("png", 10, 10)).andExpect(status().isOk());
        putAvec(jetonAdmin, REGLAGES, Map.of("nomAffiche", "Coopérative A")).andExpect(status().isOk());

        adminActif("autre@test.com");
        String jetonAutre = jeton("autre@test.com");
        getAvec(jetonAutre, LECTURE).andExpect(status().isOk())
                .andExpect(jsonPath("$.nomAffiche").isEmpty())
                .andExpect(jsonPath("$.logo").isEmpty());
        putAvec(jetonAutre, REGLAGES, Map.of("nomAffiche", "Coopérative B")).andExpect(status().isOk());
        deleteAvec(jetonAutre, LOGO).andExpect(status().isOk());

        // Rien n'a changé pour la première entreprise.
        getAvec(jetonAdmin, LECTURE)
                .andExpect(jsonPath("$.nomAffiche").value("Coopérative A"))
                .andExpect(jsonPath("$.logo.typeMime").value("image/png"));
        // Le super admin n'a pas de personnalisation à modifier.
        superAdmin("sa@test.com");
        putAvec(jeton("sa@test.com"), REGLAGES, Map.of("nomAffiche", "X")).andExpect(status().isForbidden());
    }

    // ---------------------------------------------------------------- Couleur et nom

    @Test
    void couleurEtNomAffiche() throws Exception {
        // Par défaut : couleur Batulo, nom de l'entreprise.
        getAvec(jetonAdmin, LECTURE).andExpect(status().isOk())
                .andExpect(jsonPath("$.couleurPrincipale").isEmpty())
                .andExpect(jsonPath("$.couleurEffective").value("#005CBB"))
                .andExpect(jsonPath("$.couleurTexte").value("#FFFFFF"))
                .andExpect(jsonPath("$.nomEffectif").value("Entreprise de awa@test.com"));

        for (String invalide : List.of("#12345", "red", "#GGGGGG", "1E6B52", "#1E6B52FF")) {
            putAvec(jetonAdmin, REGLAGES, Map.of("couleurPrincipale", invalide)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COULEUR_INVALIDE"));
        }
        // Normalisée en majuscules ; texte noir sur une couleur claire.
        putAvec(jetonAdmin, REGLAGES, Map.of("couleurPrincipale", "#ffd60a", "nomAffiche", "  Coopérative de Kaolack  "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.couleurPrincipale").value("#FFD60A"))
                .andExpect(jsonPath("$.couleurTexte").value("#000000"))
                .andExpect(jsonPath("$.nomAffiche").value("Coopérative de Kaolack"))
                .andExpect(jsonPath("$.nomEffectif").value("Coopérative de Kaolack"));
        // Champ absent : inchangé.
        putAvec(jetonAdmin, REGLAGES, Map.of("couleurPrincipale", "#000080")).andExpect(status().isOk())
                .andExpect(jsonPath("$.couleurTexte").value("#FFFFFF"))
                .andExpect(jsonPath("$.nomAffiche").value("Coopérative de Kaolack"));
        // "" : effacé, retour aux valeurs par défaut.
        putAvec(jetonAdmin, REGLAGES, Map.of("couleurPrincipale", "", "nomAffiche", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.couleurPrincipale").isEmpty())
                .andExpect(jsonPath("$.couleurEffective").value("#005CBB"))
                .andExpect(jsonPath("$.nomAffiche").isEmpty());
        putAvec(jetonAdmin, REGLAGES, Map.of("nomAffiche", "x".repeat(151))).andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------- Formulaire, aperçu, PDF, super admin

    @Test
    void formulairePublicEtApercuSansPersonnalisation() throws Exception {
        long sondageId = sondagePublie();
        JsonNode apparence = lire(mockMvc.perform(get("/api/public/sondages/" + lien(sondageId)))
                .andExpect(status().isOk())).get("apparence");
        assertThat(champs(apparence)).containsExactlyInAnyOrder("nomAffiche", "logoUrl", "couleurPrincipale",
                "couleurTexte");
        assertThat(apparence.get("nomAffiche").asText()).isEqualTo("Entreprise de awa@test.com");
        assertThat(apparence.get("logoUrl").isNull()).isTrue();
        assertThat(apparence.get("couleurPrincipale").asText()).isEqualTo("#005CBB");
        assertThat(apparence.get("couleurTexte").asText()).isEqualTo("#FFFFFF");

        getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/apercu").andExpect(status().isOk())
                .andExpect(jsonPath("$.apparence.couleurPrincipale").value("#005CBB"));
    }

    @Test
    void formulairePublicEtApercuPersonnalises() throws Exception {
        long sondageId = sondagePublie();
        String url = lire(envoyerLogo(jetonAdmin, "logo.png", "image/png", image("png", 40, 40))).at("/logo/url").asText();
        putAvec(jetonAdmin, REGLAGES, Map.of("couleurPrincipale", "#F5A623", "nomAffiche", "Coopérative de Kaolack"))
                .andExpect(status().isOk());

        JsonNode formulaire = lire(mockMvc.perform(get("/api/public/sondages/" + lien(sondageId))));
        JsonNode apparence = formulaire.get("apparence");
        assertThat(champs(apparence)).hasSize(4);
        assertThat(apparence.get("nomAffiche").asText()).isEqualTo("Coopérative de Kaolack");
        assertThat(apparence.get("logoUrl").asText()).isEqualTo(url);
        assertThat(apparence.get("couleurPrincipale").asText()).isEqualTo("#F5A623");
        assertThat(apparence.get("couleurTexte").asText()).isEqualTo("#000000");
        // Rien d'autre sur l'entreprise dans le formulaire.
        String texte = formulaire.toString();
        assertThat(texte).doesNotContain("Entreprise de awa@test.com").doesNotContain("entrepriseId");

        getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/apercu")
                .andExpect(jsonPath("$.apparence.logoUrl").value(url))
                .andExpect(jsonPath("$.apparence.nomAffiche").value("Coopérative de Kaolack"));
    }

    @Test
    void rapportPdfAvecLogo() throws Exception {
        long sondageId = sondagePublie();
        String sansLogo = pdf(sondageId);
        assertThat(IMAGE_PDF.matcher(sansLogo).find()).isFalse();

        envoyerLogo(jetonAdmin, "logo.png", "image/png", image("png", 200, 80)).andExpect(status().isOk());
        String avecLogo = pdf(sondageId);
        assertThat(avecLogo).startsWith("%PDF");
        assertThat(IMAGE_PDF.matcher(avecLogo).find()).isTrue();

        envoyerLogo(jetonAdmin, "logo.jpg", "image/jpeg", image("jpg", 200, 80)).andExpect(status().isOk());
        assertThat(pdf(sondageId)).contains("/DCTDecode");
    }

    @Test
    void superAdminVoitLaPersonnalisationEnLectureSeule() throws Exception {
        String url = lire(envoyerLogo(jetonAdmin, "logo.png", "image/png", image("png", 40, 40))).at("/logo/url").asText();
        putAvec(jetonAdmin, REGLAGES, Map.of("nomAffiche", "Coopérative de Kaolack")).andExpect(status().isOk());
        superAdmin("sa@test.com");
        getAvec(jeton("sa@test.com"), "/api/super-admin/admins/" + admin.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.personnalisation.nomAffiche").value("Coopérative de Kaolack"))
                .andExpect(jsonPath("$.personnalisation.logo.url").value(url))
                .andExpect(jsonPath("$.personnalisation.couleurEffective").value("#005CBB"));
    }

    // ---------------------------------------------------------------- Outils

    private ResultActions envoyerLogo(String jeton, String nom, String type, byte[] contenu) throws Exception {
        return mockMvc.perform(multipart(HttpMethod.PUT, LOGO)
                .file(new MockMultipartFile("fichier", nom, type, contenu))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jeton));
    }

    private static byte[] image(String format, int largeur, int hauteur) throws Exception {
        BufferedImage image = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        g.setColor(new Color(0x1E, 0x6B, 0x52));
        g.fillRect(0, 0, largeur, hauteur);
        g.dispose();
        ByteArrayOutputStream flux = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, flux)).isTrue();
        return flux.toByteArray();
    }

    /** PNG de bruit aléatoire : incompressible, donc plus de 1 Mo avec des dimensions permises. */
    private static byte[] bruit(int largeur, int hauteur) throws Exception {
        BufferedImage image = new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_RGB);
        Random aleatoire = new Random(42);
        for (int x = 0; x < largeur; x++) {
            for (int y = 0; y < hauteur; y++) {
                image.setRGB(x, y, aleatoire.nextInt(0xFFFFFF));
            }
        }
        ByteArrayOutputStream flux = new ByteArrayOutputStream();
        ImageIO.write(image, "png", flux);
        return flux.toByteArray();
    }

    private long sondagePublie() throws Exception {
        long id = lire(postAvec(jetonAdmin, "/api/sondages", Map.of("titre", "Enquête", "reponseUnique", false))
                .andExpect(status().isCreated())).get("id").asLong();
        long section = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/sections", Map.of("titre", "S")))
                .at("/sections/0/id").asLong();
        postAvec(jetonAdmin, "/api/sondages/" + id + "/sections/" + section + "/questions",
                SondageIntegrationTest.question("Région", "CHOIX_UNIQUE", null, "Dakar", "Thiès"))
                .andExpect(status().isCreated());
        postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
        return id;
    }

    private String lien(long sondageId) throws Exception {
        return lire(getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/lien")).get("jeton").asText();
    }

    /** PDF lu en ISO-8859-1 : les mots-clés PDF (/Subtype /Image...) restent lisibles. */
    private String pdf(long sondageId) throws Exception {
        byte[] octets = getAvec(jetonAdmin, "/api/sondages/" + sondageId + "/export/statistiques?format=PDF")
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andReturn().getResponse().getContentAsByteArray();
        return new String(octets, StandardCharsets.ISO_8859_1);
    }

    private static String chemin(String url) {
        return url.substring("http://localhost:8080".length());
    }

    private static List<String> champs(JsonNode objet) {
        List<String> champs = new ArrayList<>();
        objet.fieldNames().forEachRemaining(champs::add);
        return champs;
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
