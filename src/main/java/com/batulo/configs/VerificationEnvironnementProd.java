package com.batulo.configs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.Profiles;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Profil « prod » (Heroku) : vérifie toutes les variables d'environnement AVANT la création du contexte (aucune
 * connexion à la base, aucun service démarré) et refuse le démarrage en les listant toutes
 * ({@link ConfigurationProdInvalideException}). Sans effet hors du profil prod.
 * <p>
 * Base de données : JDBC_DATABASE_URL (+ _USERNAME, _PASSWORD), fournis par le buildpack Java de Heroku ; à défaut,
 * DATABASE_URL (postgres://utilisateur:motdepasse@hote:port/base) est converti en JDBC avec sslmode=require.
 * Enregistré dans META-INF/spring.factories ; passe après le chargement des fichiers de configuration.
 */
public class VerificationEnvironnementProd implements EnvironmentPostProcessor, Ordered {

    static final String PROFIL_PROD = "prod";
    static final String SOURCE_DATABASE_URL = "batuloDatabaseUrlHeroku";
    private static final int TAILLE_MIN_SECRET_OCTETS = 32;

    /** Toujours obligatoires en prod (la base est vérifiée à part). */
    static final List<String> OBLIGATOIRES = List.of(
            "PORT", "JWT_SECRET", "INVITATION_SECRET", "CORS_ORIGINS", "APP_BASE_URL", "PUBLIC_FORM_URL",
            "RESET_PASSWORD_URL", "MOOSYL_URL_RETOUR", "MAIL_HOST", "MAIL_PORT", "MAIL_FROM",
            "STRIPE_SECRET_KEY", "STRIPE_PUBLISHABLE_KEY", "MOOSYL_API_KEY", "MOOSYL_WEBHOOK_SECRET",
            "PRIX_SONDAGE_CAD", "PRIX_SONDAGE_MRU");

    /** Adresses publiques : https, jamais localhost. */
    private static final List<String> URLS_PUBLIQUES = List.of(
            "APP_BASE_URL", "PUBLIC_FORM_URL", "RESET_PASSWORD_URL", "MOOSYL_URL_RETOUR");

    @Override
    public int getOrder() {
        // Après ConfigDataEnvironmentPostProcessor : les profils actifs et les fichiers sont connus.
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of(PROFIL_PROD))) {
            return;
        }
        List<String> problemes = new ArrayList<>();
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            problemes.add("les profils prod et dev sont actifs ensemble : les routes /api/dev seraient exposées "
                    + "(SPRING_PROFILES_ACTIVE doit valoir prod seulement)");
        }
        verifierBase(environment, problemes);
        for (String nom : OBLIGATOIRES) {
            if (vide(environment.getProperty(nom))) {
                problemes.add(nom + " est absente");
            }
        }
        if (!"false".equalsIgnoreCase(texte(environment, "MAIL_SMTP_AUTH"))) {
            for (String nom : List.of("MAIL_USERNAME", "MAIL_PASSWORD")) {
                if (vide(environment.getProperty(nom))) {
                    problemes.add(nom + " est absente (authentification SMTP active ; MAIL_SMTP_AUTH=false sinon)");
                }
            }
        }
        verifierEntier(environment, "PORT", problemes);
        verifierEntier(environment, "MAIL_PORT", problemes);
        verifierSecret(environment, "JWT_SECRET", problemes);
        verifierSecret(environment, "INVITATION_SECRET", problemes);
        String jwt = texte(environment, "JWT_SECRET");
        if (!jwt.isEmpty() && jwt.equals(texte(environment, "INVITATION_SECRET"))) {
            problemes.add("INVITATION_SECRET doit être différente de JWT_SECRET");
        }
        for (String nom : URLS_PUBLIQUES) {
            verifierUrl(nom, texte(environment, nom), problemes);
        }
        String origines = texte(environment, "CORS_ORIGINS");
        for (String origine : origines.split(",")) {
            if (!origine.isBlank()) {
                verifierUrl("CORS_ORIGINS", origine.trim(), problemes);
            }
        }
        verifierStripe(environment, problemes);
        verifierPrix(environment, problemes);
        if (!problemes.isEmpty()) {
            throw new ConfigurationProdInvalideException(problemes);
        }
    }

    /** JDBC_DATABASE_URL, sinon DATABASE_URL converti (source ajoutée en dernier : jamais prioritaire). */
    private static void verifierBase(ConfigurableEnvironment environment, List<String> problemes) {
        if (!vide(environment.getProperty("JDBC_DATABASE_URL"))) {
            if (!texte(environment, "JDBC_DATABASE_URL").startsWith("jdbc:postgresql://")) {
                problemes.add("JDBC_DATABASE_URL doit commencer par jdbc:postgresql://");
            }
            return;
        }
        String databaseUrl = texte(environment, "DATABASE_URL");
        if (databaseUrl.isEmpty()) {
            problemes.add("JDBC_DATABASE_URL (ou DATABASE_URL) est absente : ajoutez Heroku Postgres");
            return;
        }
        try {
            Map<String, Object> valeurs = convertirDatabaseUrl(databaseUrl);
            if (!vide(environment.getProperty("JDBC_DATABASE_USERNAME"))) {
                valeurs.remove("JDBC_DATABASE_USERNAME");
                valeurs.remove("JDBC_DATABASE_PASSWORD");
            }
            environment.getPropertySources().addLast(new MapPropertySource(SOURCE_DATABASE_URL, valeurs));
        } catch (IllegalArgumentException e) {
            problemes.add("DATABASE_URL est mal formée (attendu : postgres://utilisateur:motdepasse@hote:port/base)");
        }
    }

    /** postgres://u:p@hote:port/base?x=y -> JDBC_DATABASE_URL, _USERNAME, _PASSWORD (SSL exigé par Heroku). */
    static Map<String, Object> convertirDatabaseUrl(String databaseUrl) {
        URI uri = URI.create(databaseUrl.trim());
        String schema = uri.getScheme();
        if (schema == null || !(schema.equals("postgres") || schema.equals("postgresql"))
                || uri.getHost() == null || uri.getPath() == null || uri.getPath().length() <= 1) {
            throw new IllegalArgumentException("DATABASE_URL");
        }
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String requete = uri.getRawQuery() == null ? "" : uri.getRawQuery();
        if (!requete.contains("sslmode=")) {
            requete = requete.isEmpty() ? "sslmode=require" : requete + "&sslmode=require";
        }
        Map<String, Object> valeurs = new LinkedHashMap<>();
        valeurs.put("JDBC_DATABASE_URL", "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath()
                + "?" + requete);
        String infos = uri.getRawUserInfo();
        if (infos != null && !infos.isEmpty()) {
            int separateur = infos.indexOf(':');
            String utilisateur = separateur < 0 ? infos : infos.substring(0, separateur);
            String motDePasse = separateur < 0 ? "" : infos.substring(separateur + 1);
            valeurs.put("JDBC_DATABASE_USERNAME", URLDecoder.decode(utilisateur, StandardCharsets.UTF_8));
            valeurs.put("JDBC_DATABASE_PASSWORD", URLDecoder.decode(motDePasse, StandardCharsets.UTF_8));
        }
        return valeurs;
    }

    private static void verifierEntier(ConfigurableEnvironment environment, String nom, List<String> problemes) {
        String valeur = texte(environment, nom);
        if (valeur.isEmpty()) {
            return;
        }
        try {
            int nombre = Integer.parseInt(valeur);
            if (nombre < 1 || nombre > 65535) {
                problemes.add(nom + " doit être un port entre 1 et 65535");
            }
        } catch (NumberFormatException e) {
            problemes.add(nom + " doit être un nombre entier");
        }
    }

    private static void verifierSecret(ConfigurableEnvironment environment, String nom, List<String> problemes) {
        String valeur = texte(environment, nom);
        if (!valeur.isEmpty() && valeur.getBytes(StandardCharsets.UTF_8).length < TAILLE_MIN_SECRET_OCTETS) {
            problemes.add(nom + " doit faire au moins " + TAILLE_MIN_SECRET_OCTETS + " octets");
        }
    }

    static void verifierUrl(String nom, String valeur, List<String> problemes) {
        if (valeur == null || valeur.isBlank()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(valeur.trim());
        } catch (IllegalArgumentException e) {
            problemes.add(nom + " n'est pas une adresse valide");
            return;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            problemes.add(nom + " doit être une adresse https:// (reçu : " + valeur.trim() + ")");
            return;
        }
        String hote = uri.getHost().toLowerCase(Locale.ROOT);
        if (hote.equals("localhost") || hote.endsWith(".localhost") || hote.startsWith("127.")
                || hote.equals("0.0.0.0") || hote.equals("[::1]")) {
            problemes.add(nom + " ne doit pas viser la machine locale (reçu : " + valeur.trim() + ")");
        }
    }

    /** Premier déploiement : mode test Stripe seulement (le mode live viendra avec le webhook Stripe vérifié). */
    private static void verifierStripe(ConfigurableEnvironment environment, List<String> problemes) {
        String secrete = texte(environment, "STRIPE_SECRET_KEY");
        if (!secrete.isEmpty() && !secrete.startsWith("sk_test_") && !secrete.startsWith("rk_test_")) {
            problemes.add("STRIPE_SECRET_KEY doit être une clé de test (sk_test_...) : le mode live n'est pas "
                    + "encore pris en charge");
        }
        String publique = texte(environment, "STRIPE_PUBLISHABLE_KEY");
        if (!publique.isEmpty() && !publique.startsWith("pk_test_")) {
            problemes.add("STRIPE_PUBLISHABLE_KEY doit être une clé de test (pk_test_...) : le mode live n'est pas "
                    + "encore pris en charge");
        }
    }

    private static void verifierPrix(ConfigurableEnvironment environment, List<String> problemes) {
        String carte = texte(environment, "PRIX_SONDAGE_CAD");
        if (!carte.isEmpty()) {
            try {
                BigDecimal prix = new BigDecimal(carte);
                if (prix.signum() <= 0 || prix.stripTrailingZeros().scale() > 2) {
                    problemes.add("PRIX_SONDAGE_CAD doit être positif, avec 2 décimales au plus (ex. 0.50)");
                }
            } catch (NumberFormatException e) {
                problemes.add("PRIX_SONDAGE_CAD doit être un nombre (ex. 0.50)");
            }
        }
        String mobile = texte(environment, "PRIX_SONDAGE_MRU");
        if (!mobile.isEmpty()) {
            try {
                if (Long.parseLong(mobile) <= 0) {
                    problemes.add("PRIX_SONDAGE_MRU doit être un entier positif (ex. 20)");
                }
            } catch (NumberFormatException e) {
                problemes.add("PRIX_SONDAGE_MRU doit être un entier positif (ex. 20)");
            }
        }
    }

    private static String texte(ConfigurableEnvironment environment, String nom) {
        String valeur = environment.getProperty(nom);
        return valeur == null ? "" : valeur.trim();
    }

    private static boolean vide(String valeur) {
        return valeur == null || valeur.isBlank();
    }
}
