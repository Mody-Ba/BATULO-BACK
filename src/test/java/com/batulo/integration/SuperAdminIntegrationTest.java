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

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ajouts pour l'espace super admin : tableau de bord, liste des admins enrichie et filtrée, message obligatoire
 * pour refuser une demande d'augmentation.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class SuperAdminIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    EntityManagerFactory entityManagerFactory;

    private String jetonSa;

    @BeforeEach
    void preparer() throws Exception {
        superAdmin("sa@test.com");
        jetonSa = jeton("sa@test.com");
    }

    // ------------------------------------------------------------------ Tableau de bord

    @Test
    void tableauDeBordCompteLesEntreprisesParStatutAffiche() throws Exception {
        Utilisateur actif1 = admin("actif1@test.com", dansJours(60));
        admin("actif2@test.com", dansJours(60));
        Utilisateur bientot = admin("bientot@test.com", dansJours(3));
        admin("expire@test.com", dansJours(-1));
        admin("attente@test.com", null);
        desactiver(admin("desactive@test.com", dansJours(60)));
        demande(actif1, "EN_ATTENTE");
        demande(bientot, "EN_ATTENTE");
        demande(bientot, "REFUSEE");

        getAvec(jetonSa, "/api/super-admin/tableau-de-bord")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entreprises.total").value(6))
                .andExpect(jsonPath("$.entreprises.actif").value(2))
                .andExpect(jsonPath("$.entreprises.expireBientot").value(1))
                .andExpect(jsonPath("$.entreprises.expire").value(1))
                .andExpect(jsonPath("$.entreprises.enAttentePaiement").value(1))
                .andExpect(jsonPath("$.entreprises.desactive").value(1))
                .andExpect(jsonPath("$.demandesAugmentationEnAttente").value(2));
    }

    @Test
    void tableauDeBordSansEntreprise() throws Exception {
        getAvec(jetonSa, "/api/super-admin/tableau-de-bord")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entreprises.total").value(0))
                .andExpect(jsonPath("$.entreprises.actif").value(0))
                .andExpect(jsonPath("$.entreprises.desactive").value(0))
                .andExpect(jsonPath("$.demandesAugmentationEnAttente").value(0));
    }

    @Test
    void tableauDeBordEnRequetesFixes() throws Exception {
        admin("a0@test.com", dansJours(60));
        long avecUne = requetesPour("/api/super-admin/tableau-de-bord");
        for (int i = 1; i < 10; i++) {
            admin("a" + i + "@test.com", dansJours(i * 10 - 25));
        }
        assertThat(requetesPour("/api/super-admin/tableau-de-bord")).isEqualTo(avecUne);
        // Compte connecté (filtre JWT), agrégation par statut, total des entreprises, demandes en attente.
        assertThat(avecUne).isEqualTo(4);
    }

    // ------------------------------------------------------------------ Liste des admins

    @Test
    void ligneDonneEntrepriseAdminAbonnementEtGestionnaires() throws Exception {
        Utilisateur admin = admin("awa@test.com", dansJours(3));
        gestionnaire("g1@test.com", admin);
        gestionnaire("g2@test.com", admin);
        admin("attente@test.com", null);

        JsonNode page = lire(getAvec(jetonSa, "/api/super-admin/admins").andExpect(status().isOk()));
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        // Du plus récent au plus ancien.
        JsonNode attente = page.at("/contenu/0");
        assertThat(attente.at("/admin/courriel").asText()).isEqualTo("attente@test.com");
        assertThat(attente.at("/abonnement/statut").asText()).isEqualTo("EN_ATTENTE_PAIEMENT");
        assertThat(attente.at("/abonnement/formule").isNull()).isTrue();
        assertThat(attente.at("/abonnement/joursRestants").isNull()).isTrue();
        assertThat(attente.get("nombreGestionnaires").asLong()).isZero();

        JsonNode awa = page.at("/contenu/1");
        assertThat(awa.at("/entreprise/id").asLong()).isEqualTo(admin.getEntreprise().getId());
        assertThat(awa.at("/entreprise/nom").asText()).isEqualTo("Entreprise de awa@test.com");
        assertThat(awa.at("/entreprise/actif").asBoolean()).isTrue();
        assertThat(awa.at("/admin/id").asLong()).isEqualTo(admin.getId());
        assertThat(awa.at("/admin/prenom").asText()).isEqualTo("Prénom");
        assertThat(awa.at("/admin/actif").asBoolean()).isTrue();
        assertThat(awa.at("/abonnement/formule").asText()).isEqualTo("MENSUEL");
        assertThat(awa.at("/abonnement/statut").asText()).isEqualTo("EXPIRE_BIENTOT");
        assertThat(awa.at("/abonnement/joursRestants").asLong()).isEqualTo(3);
        assertThat(awa.at("/abonnement/dateExpiration").isTextual()).isTrue();
        assertThat(awa.get("nombreGestionnaires").asLong()).isEqualTo(2);
        // Le super admin n'est pas dans la liste.
        assertThat(courriels(page)).doesNotContain("sa@test.com");
    }

    @Test
    void filtreParStatutAffiche() throws Exception {
        admin("actif@test.com", dansJours(60));
        admin("bientot@test.com", dansJours(3));
        admin("expire@test.com", dansJours(-1));
        admin("attente@test.com", null);
        desactiver(admin("desactive@test.com", dansJours(3)));

        assertThat(courriels("statut", "ACTIF")).containsExactly("actif@test.com");
        assertThat(courriels("statut", "EXPIRE_BIENTOT")).containsExactly("bientot@test.com");
        assertThat(courriels("statut", "EXPIRE")).containsExactly("expire@test.com");
        assertThat(courriels("statut", "EN_ATTENTE_PAIEMENT")).containsExactly("attente@test.com");
        assertThat(courriels("statut", "DESACTIVE")).containsExactly("desactive@test.com");
        assertThat(courriels()).hasSize(5);

        getAvec(jetonSa, "/api/super-admin/admins?statut=INCONNU")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
    }

    @Test
    void rechercheSansMajusculesNiAccentsSurLesQuatreChamps() throws Exception {
        Utilisateur elodie = admin("e.diallo@test.com", dansJours(60));
        renommer(elodie, "Élodie", "Diallo", "Société Générale");
        Utilisateur moussa = admin("contact@kane.sn", dansJours(-1));
        renommer(moussa, "Moussa", "Kané", "Boulangerie du Port");

        assertThat(courriels("recherche", "ÉLODIE")).containsExactly("e.diallo@test.com");
        assertThat(courriels("recherche", "elodie")).containsExactly("e.diallo@test.com");
        assertThat(courriels("recherche", "societe gen")).containsExactly("e.diallo@test.com");
        assertThat(courriels("recherche", "KANE")).containsExactly("contact@kane.sn");
        assertThat(courriels("recherche", "boulangerie")).containsExactly("contact@kane.sn");
        assertThat(courriels("recherche", "@KANE.SN")).containsExactly("contact@kane.sn");
        assertThat(courriels("recherche", "test.com")).containsExactly("e.diallo@test.com");
        assertThat(courriels("recherche", "introuvable")).isEmpty();
        assertThat(courriels("recherche", "  ")).hasSize(2);
    }

    @Test
    void jokersCherchesTelsQuels() throws Exception {
        renommer(admin("pourcent@test.com", dansJours(60)), "A", "B", "Taux 100% fiable");
        renommer(admin("mille@test.com", dansJours(60)), "A", "B", "Taux 1000 fiable");
        renommer(admin("souligne@test.com", dansJours(60)), "A", "B", "a_b conseil");
        renommer(admin("axb@test.com", dansJours(60)), "A", "B", "axb conseil");

        assertThat(courriels("recherche", "100%")).containsExactly("pourcent@test.com");
        assertThat(courriels("recherche", "a_b")).containsExactly("souligne@test.com");
        assertThat(courriels("recherche", "%")).containsExactly("pourcent@test.com");
    }

    @Test
    void statutEtRechercheSeCombinentEtSePaginent() throws Exception {
        for (int i = 0; i < 3; i++) {
            renommer(admin("actif" + i + "@test.com", dansJours(60)), "P", "N", "Groupe Alpha " + i);
        }
        renommer(admin("expire@test.com", dansJours(-1)), "P", "N", "Groupe Alpha expiré");
        renommer(admin("autre@test.com", dansJours(60)), "P", "N", "Beta");

        assertThat(courriels("statut", "ACTIF", "recherche", "alpha")).hasSize(3)
                .allMatch(c -> c.startsWith("actif"));
        assertThat(courriels("statut", "EXPIRE", "recherche", "alpha")).containsExactly("expire@test.com");
        assertThat(courriels("statut", "EXPIRE", "recherche", "beta")).isEmpty();

        JsonNode page0 = lire(getAvec(jetonSa, "/api/super-admin/admins?statut=ACTIF&recherche=alpha&taille=2"));
        assertThat(page0.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.get("contenu")).hasSize(2);
        JsonNode page1 = lire(getAvec(jetonSa,
                "/api/super-admin/admins?statut=ACTIF&recherche=alpha&taille=2&page=1"));
        assertThat(page1.get("contenu")).hasSize(1);
        List<String> tous = new ArrayList<>(courriels(page0));
        tous.addAll(courriels(page1));
        assertThat(tous).containsExactlyInAnyOrder("actif0@test.com", "actif1@test.com", "actif2@test.com");
    }

    @Test
    void listeSansNPlusUn() throws Exception {
        for (int i = 0; i < 2; i++) {
            Utilisateur admin = admin("a" + i + "@test.com", dansJours(60));
            gestionnaire("g" + i + "@test.com", admin);
        }
        long avecDeux = requetesPour("/api/super-admin/admins?taille=100&statut=ACTIF&recherche=test");
        for (int i = 2; i < 10; i++) {
            Utilisateur admin = admin("a" + i + "@test.com", i % 2 == 0 ? dansJours(60) : null);
            gestionnaire("g" + i + "@test.com", admin);
            gestionnaire("h" + i + "@test.com", admin);
        }
        long avecDix = requetesPour("/api/super-admin/admins?taille=100&statut=ACTIF&recherche=test");
        assertThat(avecDix).isEqualTo(avecDeux);
        assertThat(requetesPour("/api/super-admin/admins?taille=100")).isEqualTo(avecDeux);
        // Compte connecté (filtre JWT), page d'admins, total (omis si la page n'est pas pleine), abonnements,
        // gestionnaires.
        assertThat(avecDeux).isLessThanOrEqualTo(5);
    }

    // ------------------------------------------------------------------ Rôles

    @Test
    void reserveAuSuperAdmin() throws Exception {
        Utilisateur admin = adminActif("awa@test.com");
        gestionnaire("g@test.com", admin);
        String jetonAdmin = jeton("awa@test.com");
        String jetonGestionnaire = jeton("g@test.com");
        for (String url : List.of("/api/super-admin/tableau-de-bord", "/api/super-admin/admins")) {
            getAvec(jetonAdmin, url).andExpect(status().isForbidden());
            getAvec(jetonGestionnaire, url).andExpect(status().isForbidden());
            getAvec(null, url).andExpect(status().isUnauthorized());
        }
    }

    // ------------------------------------------------------------------ Demandes d'augmentation

    @Test
    void refusExigeUnMessage() throws Exception {
        adminActif("awa@test.com");
        String jetonAdmin = jeton("awa@test.com");
        long id = demander(jetonAdmin);

        traiter(id, Map.of("statut", "REFUSEE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPONSE_OBLIGATOIRE"));
        traiter(id, Map.of("statut", "REFUSEE", "reponse", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPONSE_OBLIGATOIRE"));
        assertThat(jdbcTemplate.queryForObject("select statut from demande_abonnement where id = ?", String.class, id))
                .isEqualTo("EN_ATTENTE");

        traiter(id, Map.of("statut", "REFUSEE", "reponse", "Pas de formule annuelle pour le moment."))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REFUSEE"))
                .andExpect(jsonPath("$.reponse").value("Pas de formule annuelle pour le moment."));

        // Accepter reste possible sans message.
        long autre = demander(jetonAdmin);
        traiter(autre, Map.of("statut", "TRAITEE")).andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("TRAITEE"));
    }

    // ------------------------------------------------------------------ Outils

    private static Instant dansJours(int jours) {
        return Instant.now().plus(Duration.ofDays(jours)).plus(Duration.ofHours(jours >= 0 ? -1 : 1));
    }

    private void desactiver(Utilisateur admin) {
        jdbcTemplate.update("update entreprise set actif = false where id = ?", admin.getEntreprise().getId());
        jdbcTemplate.update("update utilisateur set actif = false where id = ?", admin.getId());
    }

    private void renommer(Utilisateur admin, String prenom, String nom, String entreprise) {
        jdbcTemplate.update("update utilisateur set prenom = ?, nom = ? where id = ?", prenom, nom, admin.getId());
        jdbcTemplate.update("update entreprise set nom = ? where id = ?", entreprise, admin.getEntreprise().getId());
    }

    private void demande(Utilisateur admin, String statut) {
        jdbcTemplate.update("insert into demande_abonnement (entreprise_id, admin_id, formule_souhaitee, message, "
                        + "statut, date_demande) values (?, ?, 'ANNUEL', 'Plus d''enquêtes', ?, ?)",
                admin.getEntreprise().getId(), admin.getId(), statut, Timestamp.from(Instant.now()));
    }

    private long demander(String jetonAdmin) throws Exception {
        return lire(postAvec(jetonAdmin, "/api/abonnements/demandes",
                Map.of("formuleSouhaitee", "ANNUEL", "message", "Trois nouvelles enquêtes"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions traiter(long id, Map<String, String> corps) throws Exception {
        return mockMvc.perform(patch("/api/super-admin/demandes-abonnement/" + id)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonSa)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(corps)));
    }

    /** Courriels des admins listés, paramètres donnés par paires nom, valeur (sans encodage). */
    private List<String> courriels(String... parametres) throws Exception {
        var requete = get("/api/super-admin/admins").header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonSa);
        for (int i = 0; i < parametres.length; i += 2) {
            requete.param(parametres[i], parametres[i + 1]);
        }
        return courriels(lire(mockMvc.perform(requete).andExpect(status().isOk())));
    }

    private static List<String> courriels(JsonNode page) {
        List<String> courriels = new ArrayList<>();
        page.get("contenu").forEach(ligne -> courriels.add(ligne.at("/admin/courriel").asText()));
        return courriels;
    }

    /** Nombre de requêtes SQL préparées pour un GET du super admin. */
    private long requetesPour(String url) throws Exception {
        Statistics statistiques = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistiques.clear();
        mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonSa)).andExpect(status().isOk());
        return statistiques.getPrepareStatementCount();
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
