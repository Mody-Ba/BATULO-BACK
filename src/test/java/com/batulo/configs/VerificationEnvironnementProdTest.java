package com.batulo.configs;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.mock.env.MockEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Refus de démarrer en prod : variables manquantes ou invalides, toutes listées, sans recopier les secrets. */
class VerificationEnvironnementProdTest {

    private final VerificationEnvironnementProd verification = new VerificationEnvironnementProd();

    private static Map<String, String> completes() {
        Map<String, String> valeurs = new LinkedHashMap<>();
        valeurs.put("JDBC_DATABASE_URL", "jdbc:postgresql://hote.exemple:5432/base?sslmode=require");
        valeurs.put("PORT", "12345");
        valeurs.put("JWT_SECRET", "secret-jwt-fictif-de-plus-de-trente-deux-octets");
        valeurs.put("INVITATION_SECRET", "secret-invitations-fictif-de-plus-de-32-octets");
        valeurs.put("CORS_ORIGINS", "https://batulo.web.app,https://batulo.firebaseapp.com");
        valeurs.put("APP_BASE_URL", "https://batulo-api.herokuapp.com");
        valeurs.put("PUBLIC_FORM_URL", "https://batulo.web.app/s");
        valeurs.put("RESET_PASSWORD_URL", "https://batulo.web.app/reinitialiser-mot-de-passe");
        valeurs.put("MOOSYL_URL_RETOUR", "https://batulo.web.app/admin/abonnement/paiement-mobile");
        valeurs.put("MAIL_HOST", "smtp.exemple.com");
        valeurs.put("MAIL_PORT", "587");
        valeurs.put("MAIL_USERNAME", "utilisateur");
        valeurs.put("MAIL_PASSWORD", "mot-de-passe-smtp-tres-secret");
        valeurs.put("MAIL_FROM", "Batulo <no-reply@exemple.com>");
        valeurs.put("STRIPE_SECRET_KEY", "sk_test_fictive");
        valeurs.put("STRIPE_PUBLISHABLE_KEY", "pk_test_fictive");
        valeurs.put("MOOSYL_API_KEY", "cle-moosyl");
        valeurs.put("MOOSYL_WEBHOOK_SECRET", "secret-webhook");
        valeurs.put("PRIX_SONDAGE_CAD", "0.50");
        valeurs.put("PRIX_SONDAGE_MRU", "20");
        return valeurs;
    }

    private MockEnvironment environnement(Map<String, String> valeurs, String... profils) {
        MockEnvironment environnement = new MockEnvironment();
        valeurs.forEach(environnement::setProperty);
        environnement.setActiveProfiles(profils);
        return environnement;
    }

    private void verifier(MockEnvironment environnement) {
        verification.postProcessEnvironment(environnement, new SpringApplication());
    }

    @Test
    void accepteUneConfigurationComplete() {
        verifier(environnement(completes(), "prod"));
    }

    @Test
    void sansEffetHorsDuProfilProd() {
        verifier(environnement(Map.of(), "dev"));
        verifier(environnement(Map.of()));
    }

    @Test
    void listeToutesLesVariablesManquantes() {
        assertThatThrownBy(() -> verifier(environnement(Map.of(), "prod")))
                .isInstanceOf(ConfigurationProdInvalideException.class)
                .satisfies(e -> {
                    var problemes = ((ConfigurationProdInvalideException) e).getProblemes();
                    assertThat(problemes).anyMatch(p -> p.startsWith("JDBC_DATABASE_URL (ou DATABASE_URL)"));
                    for (String nom : VerificationEnvironnementProd.OBLIGATOIRES) {
                        assertThat(problemes).contains(nom + " est absente");
                    }
                    assertThat(problemes).anyMatch(p -> p.startsWith("MAIL_USERNAME est absente"));
                    assertThat(problemes).anyMatch(p -> p.startsWith("MAIL_PASSWORD est absente"));
                });
    }

    @Test
    void uneSeuleVariableManquante() {
        Map<String, String> valeurs = completes();
        valeurs.remove("INVITATION_SECRET");
        assertThatThrownBy(() -> verifier(environnement(valeurs, "prod")))
                .isInstanceOf(ConfigurationProdInvalideException.class)
                .satisfies(e -> assertThat(((ConfigurationProdInvalideException) e).getProblemes())
                        .containsExactly("INVITATION_SECRET est absente"));
    }

    @Test
    void smtpSansAuthentificationNExigePasDIdentifiants() {
        Map<String, String> valeurs = completes();
        valeurs.remove("MAIL_USERNAME");
        valeurs.remove("MAIL_PASSWORD");
        valeurs.put("MAIL_SMTP_AUTH", "false");
        verifier(environnement(valeurs, "prod"));
    }

    @Test
    void refuseLesValeursInvalidesSansRecopierLesSecrets() {
        Map<String, String> valeurs = completes();
        valeurs.put("JWT_SECRET", "trop-court");
        valeurs.put("PUBLIC_FORM_URL", "http://batulo.web.app/s");
        valeurs.put("RESET_PASSWORD_URL", "https://localhost:4200/reinitialiser");
        valeurs.put("CORS_ORIGINS", "https://batulo.web.app,http://localhost:4200");
        valeurs.put("STRIPE_SECRET_KEY", "sk_live_cle_reelle_secrete");
        valeurs.put("STRIPE_PUBLISHABLE_KEY", "pk_live_cle");
        valeurs.put("PRIX_SONDAGE_CAD", "0.505");
        valeurs.put("PRIX_SONDAGE_MRU", "-3");
        valeurs.put("PORT", "web");
        assertThatThrownBy(() -> verifier(environnement(valeurs, "prod")))
                .isInstanceOf(ConfigurationProdInvalideException.class)
                .satisfies(e -> {
                    var problemes = ((ConfigurationProdInvalideException) e).getProblemes();
                    assertThat(problemes).hasSize(9);
                    assertThat(e.getMessage()).doesNotContain("sk_live_cle_reelle_secrete", "trop-court");
                    assertThat(problemes).anyMatch(p -> p.startsWith("JWT_SECRET doit faire au moins 32"));
                    assertThat(problemes).anyMatch(p -> p.startsWith("PUBLIC_FORM_URL doit être une adresse https://"));
                    assertThat(problemes).anyMatch(p -> p.startsWith("RESET_PASSWORD_URL ne doit pas viser la machine"));
                    assertThat(problemes).anyMatch(p -> p.startsWith("CORS_ORIGINS doit être une adresse https://"));
                    assertThat(problemes).anyMatch(p -> p.startsWith("STRIPE_SECRET_KEY doit être une clé de test"));
                    assertThat(problemes).anyMatch(p -> p.startsWith("PORT doit être un nombre entier"));
                });
    }

    @Test
    void refuseLesSecretsIdentiques() {
        Map<String, String> valeurs = completes();
        valeurs.put("INVITATION_SECRET", valeurs.get("JWT_SECRET"));
        assertThatThrownBy(() -> verifier(environnement(valeurs, "prod")))
                .hasMessageContaining("INVITATION_SECRET doit être différente de JWT_SECRET");
    }

    @Test
    void refuseProdEtDevEnsemble() {
        assertThatThrownBy(() -> verifier(environnement(completes(), "prod", "dev")))
                .hasMessageContaining("les profils prod et dev sont actifs ensemble");
    }

    @Test
    void convertitDatabaseUrlDeHeroku() {
        Map<String, String> valeurs = completes();
        valeurs.remove("JDBC_DATABASE_URL");
        valeurs.put("DATABASE_URL", "postgres://utilisateur:mot%40passe@ec2-1-2-3-4.compute.amazonaws.com:5432/d1abc");
        MockEnvironment environnement = environnement(valeurs, "prod");
        verifier(environnement);
        assertThat(environnement.getProperty("JDBC_DATABASE_URL"))
                .isEqualTo("jdbc:postgresql://ec2-1-2-3-4.compute.amazonaws.com:5432/d1abc?sslmode=require");
        assertThat(environnement.getProperty("JDBC_DATABASE_USERNAME")).isEqualTo("utilisateur");
        assertThat(environnement.getProperty("JDBC_DATABASE_PASSWORD")).isEqualTo("mot@passe");
    }

    @Test
    void conversionGardeLeSslmodeFourniEtLePortParDefaut() {
        assertThat(VerificationEnvironnementProd.convertirDatabaseUrl("postgresql://u:p@hote/base?sslmode=disable"))
                .containsEntry("JDBC_DATABASE_URL", "jdbc:postgresql://hote:5432/base?sslmode=disable");
    }

    @Test
    void refuseUneDatabaseUrlMalFormee() {
        Map<String, String> valeurs = completes();
        valeurs.remove("JDBC_DATABASE_URL");
        valeurs.put("DATABASE_URL", "mysql://u:p@hote/base");
        assertThatThrownBy(() -> verifier(environnement(valeurs, "prod")))
                .hasMessageContaining("DATABASE_URL est mal formée");
    }

    /** Démarrage réel de l'application en prod sans aucune variable : refus avant la connexion à la base. */
    @Test
    void demarrageRefuseSansVariable() {
        SpringApplication application = new SpringApplication(com.batulo.BatuloApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setAdditionalProfiles("prod");
        assertThatThrownBy(() -> application.run("--spring.main.banner-mode=off"))
                .isInstanceOf(ConfigurationProdInvalideException.class)
                .hasMessageContaining("JWT_SECRET est absente");
    }
}
