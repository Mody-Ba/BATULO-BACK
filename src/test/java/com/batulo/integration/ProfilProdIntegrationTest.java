package com.batulo.integration;

import com.batulo.controller.DevPaiementController;
import com.batulo.controller.DevSondageController;
import com.batulo.controller.DevTacheController;
import com.batulo.entity.Abonnement;
import com.batulo.entity.Entreprise;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Formule;
import com.batulo.enums.Role;
import com.batulo.enums.StatutAbonnement;
import com.batulo.repository.AbonnementRepository;
import com.batulo.repository.EntrepriseRepository;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.email.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Profil « prod » tel que sur Heroku : vrai serveur HTTP (Tomcat, en-têtes X-Forwarded-* compris), PostgreSQL 16
 * (Testcontainers) atteint par DATABASE_URL converti, toutes les valeurs données par des « variables
 * d'environnement » de test (propriétés système, lues avant la création du contexte). Aucun appel extérieur.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
class ProfilProdIntegrationTest {

    private static final String ORIGINE_FRONT = "https://batulo-test.web.app";
    private static final String MOT_DE_PASSE = "MotDePasse123";
    private static final Map<String, String> VARIABLES = new LinkedHashMap<>();

    static {
        var postgres = PostgresIntegrationTestBase.POSTGRES;
        // Comme Heroku Postgres, mais sans SSL (conteneur local) : sslmode fourni, donc conservé par la conversion.
        VARIABLES.put("DATABASE_URL", "postgres://" + postgres.getUsername() + ":" + postgres.getPassword() + "@"
                + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + postgres.getDatabaseName()
                + "?sslmode=disable");
        VARIABLES.put("PORT", "8080");
        VARIABLES.put("JWT_SECRET", "secret-jwt-de-test-du-profil-prod-au-moins-32-octets");
        VARIABLES.put("INVITATION_SECRET", "secret-invitations-de-test-du-profil-prod-32-octets");
        VARIABLES.put("CORS_ORIGINS", ORIGINE_FRONT);
        VARIABLES.put("APP_BASE_URL", "https://batulo-api-test.herokuapp.com");
        VARIABLES.put("PUBLIC_FORM_URL", ORIGINE_FRONT + "/s");
        VARIABLES.put("RESET_PASSWORD_URL", ORIGINE_FRONT + "/reinitialiser-mot-de-passe");
        VARIABLES.put("MOOSYL_URL_RETOUR", ORIGINE_FRONT + "/admin/abonnement/paiement-mobile");
        VARIABLES.put("MAIL_HOST", "smtp.exemple.invalid");
        VARIABLES.put("MAIL_PORT", "587");
        VARIABLES.put("MAIL_USERNAME", "utilisateur-smtp");
        VARIABLES.put("MAIL_PASSWORD", "mot-de-passe-smtp");
        VARIABLES.put("MAIL_FROM", "Batulo <no-reply@exemple.invalid>");
        VARIABLES.put("STRIPE_SECRET_KEY", "sk_test_cle_fictive");
        VARIABLES.put("STRIPE_PUBLISHABLE_KEY", "pk_test_cle_fictive");
        VARIABLES.put("MOOSYL_API_KEY", "cle-moosyl-fictive");
        VARIABLES.put("MOOSYL_WEBHOOK_SECRET", "secret-webhook-moosyl-fictif");
        VARIABLES.put("PRIX_SONDAGE_CAD", "0.50");
        VARIABLES.put("PRIX_SONDAGE_MRU", "20");
        // Limite des routes publiques basse : vérifie l'adresse du client vue derrière le proxy.
        VARIABLES.put("PUBLIC_RATE_GET", "3");
        // Pas de passage planifié pendant le test (le 31 février n'existe pas).
        VARIABLES.put("ABONNEMENT_CRON", "0 0 0 31 2 *");
        VARIABLES.forEach(System::setProperty);
    }

    @AfterAll
    static void retirerVariables() {
        VARIABLES.keySet().forEach(System::clearProperty);
    }

    @LocalServerPort
    int port;
    @Autowired
    ApplicationContext contexte;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbcTemplate;
    @Autowired
    UtilisateurRepository utilisateurRepository;
    @Autowired
    EntrepriseRepository entrepriseRepository;
    @Autowired
    AbonnementRepository abonnementRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @MockitoBean
    EmailService emailService;

    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Test
    void demarreAvecLiquibaseEtValidationDuSchema() throws Exception {
        assertThat(contexte.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        Integer changesets = jdbcTemplate.queryForObject(
                "select count(*) from databasechangelog where id = '024-creer-table-credit-sondage'", Integer.class);
        assertThat(changesets).isEqualTo(1);

        HttpResponse<String> formules = envoyer(requete("/api/abonnements/formules").GET());
        assertThat(formules.statusCode()).isEqualTo(200);
        // HSTS sur une réponse https (vue comme telle grâce à X-Forwarded-Proto).
        assertThat(formules.headers().firstValue("Strict-Transport-Security")).isPresent();
    }

    @Test
    void routesDevAbsentes() throws Exception {
        assertThat(contexte.getBeansOfType(DevPaiementController.class)).isEmpty();
        assertThat(contexte.getBeansOfType(DevSondageController.class)).isEmpty();
        assertThat(contexte.getBeansOfType(DevTacheController.class)).isEmpty();

        String jeton = connexionAdmin("prod-dev-absente@test.local");
        HttpResponse<String> reponse = envoyer(requete("/api/dev/paiements/carte-test")
                .header("Authorization", "Bearer " + jeton)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"formule\":\"MENSUEL\"}")));
        assertThat(reponse.statusCode()).isEqualTo(404);
        assertThat(json(reponse).get("code").asText()).isEqualTo("INTROUVABLE");

        HttpResponse<String> autre = envoyer(requete("/api/dev/sondages/1/reponses-aleatoires")
                .header("Authorization", "Bearer " + jeton)
                .POST(HttpRequest.BodyPublishers.noBody()));
        assertThat(autre.statusCode()).isNotEqualTo(200);
    }

    @Test
    void swaggerEtOpenApiDesactives() throws Exception {
        for (String chemin : new String[]{"/v3/api-docs", "/swagger-ui.html", "/swagger-ui/index.html"}) {
            HttpResponse<String> anonyme = envoyer(requete(chemin).GET());
            assertThat(anonyme.statusCode()).as(chemin).isEqualTo(401);
        }
        String jeton = connexionAdmin("prod-swagger@test.local");
        HttpResponse<String> connecte = envoyer(requete("/v3/api-docs").header("Authorization", "Bearer " + jeton).GET());
        assertThat(connecte.statusCode()).isIn(403, 404);
        assertThat(connecte.body()).doesNotContain("openapi");
    }

    @Test
    void erreursSansDetailTechnique() throws Exception {
        HttpResponse<String> malForme = envoyer(requete("/api/auth/connexion")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{pas du json")));
        assertThat(malForme.statusCode()).isEqualTo(400);
        JsonNode corps = json(malForme);
        assertThat(corps.get("code").asText()).isEqualTo("REQUETE_INVALIDE");
        assertThat(malForme.body()).doesNotContain("Exception", "trace", "at com.");

        // Page d'erreur de Spring appelée directement : ni trace, ni exception, ni page « Whitelabel ».
        HttpResponse<String> erreur = envoyer(requete("/error").GET());
        assertThat(erreur.body()).doesNotContain("Whitelabel", "trace", "exception", "Exception");
    }

    @Test
    void httpRedirigeVersHttps() throws Exception {
        HttpResponse<String> reponse = client.send(HttpRequest.newBuilder(uri("/api/abonnements/formules"))
                .header("X-Forwarded-Proto", "http").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(reponse.statusCode()).isEqualTo(302);
        assertThat(reponse.headers().firstValue("Location").orElseThrow())
                .startsWith("https://localhost/api/abonnements/formules");

        // Sans en-tête du proxy, sur le port interne (aléatoire comme sur Heroku) : redirigé, jamais 500.
        HttpResponse<String> direct = client.send(HttpRequest.newBuilder(uri("/api/abonnements/formules")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(direct.statusCode()).isEqualTo(302);
        assertThat(direct.headers().firstValue("Location").orElseThrow())
                .matches("https://localhost(:443)?/api/abonnements/formules");
    }

    @Test
    void aucunUtilisateurEnMemoireNiMotDePasseGenere() {
        assertThat(contexte.getBeanNamesForType(
                org.springframework.security.core.userdetails.UserDetailsService.class)).isEmpty();
    }

    @Test
    void corsLimiteAuxOriginesConfigurees() throws Exception {
        HttpResponse<String> autorisee = envoyer(requete("/api/public/sondages/jeton-inconnu")
                .header("X-Forwarded-For", "203.0.113.50")
                .header("Origin", ORIGINE_FRONT)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "x-jeton-invitation, x-jeton-repondant")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()));
        assertThat(autorisee.statusCode()).isEqualTo(200);
        assertThat(autorisee.headers().firstValue("Access-Control-Allow-Origin")).contains(ORIGINE_FRONT);
        assertThat(autorisee.headers().firstValue("Access-Control-Allow-Headers").orElse("").toLowerCase())
                .contains("x-jeton-invitation");

        HttpResponse<String> refusee = envoyer(requete("/api/abonnements/formules")
                .header("Origin", "https://site-inconnu.example")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()));
        assertThat(refusee.statusCode()).isEqualTo(403);
        assertThat(refusee.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    /**
     * Le routeur Heroku AJOUTE l'adresse du client à X-Forwarded-For : la limite par IP compte cette dernière valeur,
     * jamais celle, inventable, que le client a mise devant.
     */
    @Test
    void adresseDuClientVueDerriereLeProxy() throws Exception {
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> reponse = getPublic("10.9.9." + i + ", 198.51.100.7");
            assertThat(reponse.statusCode()).isNotEqualTo(429);
        }
        // Même client réel, autre adresse inventée devant : toujours limité.
        assertThat(getPublic("192.0.2.200, 198.51.100.7").statusCode()).isEqualTo(429);
        // Autre client réel : non limité.
        assertThat(getPublic("198.51.100.7, 198.51.100.8").statusCode()).isNotEqualTo(429);
    }

    private HttpResponse<String> getPublic(String xForwardedFor) throws Exception {
        return envoyer(requete("/api/public/sondages/jeton-inconnu").header("X-Forwarded-For", xForwardedFor).GET());
    }

    private String connexionAdmin(String courriel) throws Exception {
        jdbcTemplate.update("delete from entreprise where nom = ?", "Entreprise de " + courriel);
        jdbcTemplate.update("delete from utilisateur where courriel = ?", courriel);
        Entreprise entreprise = new Entreprise();
        entreprise.setNom("Entreprise de " + courriel);
        entreprise.setActif(true);
        entreprise = entrepriseRepository.save(entreprise);
        Abonnement abonnement = new Abonnement();
        abonnement.setEntreprise(entreprise);
        abonnement.setStatut(StatutAbonnement.ACTIF);
        abonnement.setFormule(Formule.MENSUEL);
        abonnement.setDateDebut(Instant.now());
        abonnement.setDateExpiration(Instant.now().plus(Duration.ofDays(30)));
        abonnement.setMontant(new BigDecimal("1.00"));
        abonnement.setDevise("CAD");
        abonnementRepository.save(abonnement);
        Utilisateur admin = new Utilisateur();
        admin.setPrenom("Prénom");
        admin.setNom("Nom");
        admin.setCourriel(courriel);
        admin.setMotDePasse(passwordEncoder.encode(MOT_DE_PASSE));
        admin.setRole(Role.ADMIN);
        admin.setEntreprise(entreprise);
        admin.setActif(true);
        utilisateurRepository.save(admin);

        HttpResponse<String> reponse = envoyer(requete("/api/auth/connexion")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(
                        Map.of("courriel", courriel, "motDePasse", MOT_DE_PASSE)))));
        assertThat(reponse.statusCode()).isEqualTo(200);
        return json(reponse).get("jeton").asText();
    }

    /** Requête vue comme arrivée en https par le routeur (X-Forwarded-Proto), depuis une adresse propre au test. */
    private HttpRequest.Builder requete(String chemin) {
        return HttpRequest.newBuilder(uri(chemin)).header("X-Forwarded-Proto", "https");
    }

    private URI uri(String chemin) {
        return URI.create("http://localhost:" + port + chemin);
    }

    private HttpResponse<String> envoyer(HttpRequest.Builder requete) throws Exception {
        return client.send(requete.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> reponse) throws Exception {
        return objectMapper.readTree(reponse.body());
    }
}
