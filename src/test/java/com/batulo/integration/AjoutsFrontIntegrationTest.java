package com.batulo.integration;

import com.batulo.entity.Abonnement;
import com.batulo.entity.Paiement;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Formule;
import com.batulo.enums.StatutPaiement;
import com.batulo.repository.PaiementRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ajouts pour le frontend : tableau de bord de l'admin, filtres des gestionnaires, compléments de la liste des
 * sondages (réponses, assignés, dernière version, brouillon en préparation) et historique des paiements.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class AjoutsFrontIntegrationTest extends PostgresIntegrationTestBase {

    @Autowired
    PaiementRepository paiementRepository;
    @Autowired
    EntityManagerFactory entityManagerFactory;

    private Utilisateur admin;
    private String jetonAdmin;

    @BeforeEach
    void preparer() throws Exception {
        admin = adminActif("awa@test.com");
        jetonAdmin = jeton("awa@test.com");
    }

    // ------------------------------------------------------------------ Sondages : dernière version

    /**
     * A : v1 EN_COURS + v2 brouillon ; B : brouillon seul ; C : v1 TERMINE + v2 EN_COURS ; D : v1 planifiée ;
     * E : v1 fermée.
     */
    private Map<String, Long> lignees() throws Exception {
        Map<String, Long> ids = new HashMap<>();
        ids.put("A1", sondagePublie(jetonAdmin, "A"));
        ids.put("A2", nouvelleVersion(jetonAdmin, ids.get("A1")));
        ids.put("B", sondageAvecQuestion(jetonAdmin, "B"));
        ids.put("C1", sondagePublie(jetonAdmin, "C"));
        ids.put("C2", nouvelleVersion(jetonAdmin, ids.get("C1")));
        postAvec(jetonAdmin, "/api/sondages/" + ids.get("C2") + "/publier", Map.of()).andExpect(status().isOk());
        ids.put("D", sondageAvecQuestion(jetonAdmin, "D"));
        postAvec(jetonAdmin, "/api/sondages/" + ids.get("D") + "/publier",
                Map.of("datePublication", Instant.now().plus(Duration.ofDays(3)).toString()))
                .andExpect(status().isOk());
        ids.put("E", sondagePublie(jetonAdmin, "E"));
        postAvec(jetonAdmin, "/api/sondages/" + ids.get("E") + "/fermer", Map.of()).andExpect(status().isOk());
        return ids;
    }

    @Test
    void derniereVersionGardeLaVersionDemarreeEtSignaleLeBrouillon() throws Exception {
        Map<String, Long> ids = lignees();

        getAvec(jetonAdmin, "/api/sondages").andExpect(jsonPath("$.totalElements").value(7));

        Map<Long, JsonNode> lignes = parId(lire(getAvec(jetonAdmin, "/api/sondages?derniereVersion=true")
                .andExpect(status().isOk())));
        assertThat(lignes.keySet()).containsExactlyInAnyOrder(
                ids.get("A1"), ids.get("B"), ids.get("C2"), ids.get("D"), ids.get("E"));

        JsonNode a1 = lignes.get(ids.get("A1"));
        assertThat(a1.get("statut").asText()).isEqualTo("EN_COURS");
        assertThat(a1.get("brouillonEnPreparation").asBoolean()).isTrue();
        assertThat(a1.get("brouillonEnPreparationId").asLong()).isEqualTo(ids.get("A2"));
        assertThat(lignes.get(ids.get("C2")).get("statut").asText()).isEqualTo("EN_COURS");
        assertThat(lignes.get(ids.get("C2")).get("brouillonEnPreparation").asBoolean()).isFalse();
        assertThat(lignes.get(ids.get("C2")).get("brouillonEnPreparationId").isNull()).isTrue();
        assertThat(lignes.get(ids.get("B")).get("brouillonEnPreparation").asBoolean()).isFalse();
        assertThat(lignes.get(ids.get("D")).get("statut").asText()).isEqualTo("NON_COMMENCE");

        // Le statut filtre la ligne représentante : le brouillon A2 n'apparaît pas en NON_COMMENCE.
        Map<Long, JsonNode> nonCommences = parId(lire(getAvec(jetonAdmin,
                "/api/sondages?derniereVersion=true&statut=NON_COMMENCE")));
        assertThat(nonCommences.keySet()).containsExactlyInAnyOrder(ids.get("B"), ids.get("D"));
        Map<Long, JsonNode> termines = parId(lire(getAvec(jetonAdmin,
                "/api/sondages?derniereVersion=true&statut=TERMINE")));
        assertThat(termines.keySet()).containsExactly(ids.get("E"));

        // Sans le filtre, la v1 de A signale aussi son brouillon ; le brouillon lui-même, non.
        Map<Long, JsonNode> toutes = parId(lire(getAvec(jetonAdmin, "/api/sondages?taille=100")));
        assertThat(toutes.get(ids.get("A2")).get("brouillonEnPreparation").asBoolean()).isFalse();
        assertThat(toutes.get(ids.get("A1")).get("brouillonEnPreparationId").asLong()).isEqualTo(ids.get("A2"));
    }

    @Test
    void lignePlanifieeEtBrouillonSansVersionDemarree() throws Exception {
        // v1 planifiée puis v2 brouillon : aucune version démarrée, la ligne est le brouillon.
        long v1 = sondageAvecQuestion(jetonAdmin, "Planifié");
        postAvec(jetonAdmin, "/api/sondages/" + v1 + "/publier",
                Map.of("datePublication", Instant.now().plus(Duration.ofDays(3)).toString()))
                .andExpect(status().isOk());
        long v2 = nouvelleVersion(jetonAdmin, v1);

        getAvec(jetonAdmin, "/api/sondages?derniereVersion=true")
                .andExpect(jsonPath("$.contenu", hasSize(1)))
                .andExpect(jsonPath("$.contenu[0].id").value(v2))
                .andExpect(jsonPath("$.contenu[0].brouillonEnPreparation").value(false));
    }

    @Test
    void reponsesEtAssignesDansLaListe() throws Exception {
        Map<String, Long> ids = lignees();
        repondre(ids.get("A1"), ids.get("A1"), 3);
        repondre(ids.get("C1"), ids.get("C1"), 2);
        repondre(ids.get("C2"), ids.get("C1"), 1);
        Utilisateur g1 = gestionnaire("g1@test.com", admin, true);
        Utilisateur g2 = gestionnaire("g2@test.com", admin, false);
        g2.setPrenom("Bintou");
        utilisateurRepository.save(g2);
        // L'assignation porte sur la lignée : demander par la v2 assigne la lignée C.
        postAvec(jetonAdmin, "/api/sondages/" + ids.get("C2") + "/assignations",
                Map.of("gestionnaireIds", List.of(g1.getId(), g2.getId()))).andExpect(status().isOk());

        Map<Long, JsonNode> lignes = parId(lire(getAvec(jetonAdmin, "/api/sondages?taille=100")));
        JsonNode a1 = lignes.get(ids.get("A1"));
        assertThat(a1.get("nombreReponses").asLong()).isEqualTo(3);
        assertThat(a1.get("nombreReponsesLignee").asLong()).isEqualTo(3);
        assertThat(a1.get("gestionnairesAssignes")).isEmpty();
        assertThat(lignes.get(ids.get("A2")).get("nombreReponses").asLong()).isZero();
        assertThat(lignes.get(ids.get("A2")).get("nombreReponsesLignee").asLong()).isEqualTo(3);
        JsonNode c2 = lignes.get(ids.get("C2"));
        assertThat(c2.get("nombreReponses").asLong()).isEqualTo(1);
        assertThat(c2.get("nombreReponsesLignee").asLong()).isEqualTo(3);
        assertThat(lignes.get(ids.get("C1")).get("nombreReponses").asLong()).isEqualTo(2);
        // Les deux versions de C partagent les assignés, triés par prénom (Bintou avant Prénom).
        for (Long id : List.of(ids.get("C1"), ids.get("C2"))) {
            JsonNode assignes = lignes.get(id).get("gestionnairesAssignes");
            assertThat(assignes).hasSize(2);
            assertThat(assignes.get(0).get("id").asLong()).isEqualTo(g2.getId());
            assertThat(assignes.get(0).get("prenom").asText()).isEqualTo("Bintou");
            assertThat(assignes.get(0).get("actif").asBoolean()).isTrue();
            assertThat(assignes.get(1).get("id").asLong()).isEqualTo(g1.getId());
        }

        // Le gestionnaire assigné voit la lignée, mais pas la liste des assignés.
        String jetonG1 = jeton("g1@test.com");
        JsonNode vueG1 = lire(getAvec(jetonG1, "/api/sondages?derniereVersion=true").andExpect(status().isOk()));
        assertThat(vueG1.at("/contenu")).hasSize(1);
        assertThat(vueG1.at("/contenu/0/id").asLong()).isEqualTo(ids.get("C2"));
        assertThat(vueG1.at("/contenu/0/nombreReponsesLignee").asLong()).isEqualTo(3);
        assertThat(vueG1.at("/contenu/0/gestionnairesAssignes").isNull()).isTrue();
    }

    @Test
    void listeDesSondagesSansRequeteParLigne() throws Exception {
        long premier = sondagePublie(jetonAdmin, "S0");
        repondre(premier, premier, 1);
        long avecUn = requetesPour("/api/sondages?taille=100");
        long avecUnDerniere = requetesPour("/api/sondages?taille=100&derniereVersion=true");

        Utilisateur g1 = gestionnaire("g1@test.com", admin, true);
        for (int i = 1; i <= 8; i++) {
            long id = sondagePublie(jetonAdmin, "S" + i);
            repondre(id, id, 2);
            postAvec(jetonAdmin, "/api/sondages/" + id + "/assignations", Map.of("gestionnaireIds", List.of(g1.getId())))
                    .andExpect(status().isOk());
            if (i % 2 == 0) {
                nouvelleVersion(jetonAdmin, id);
            }
        }
        getAvec(jetonAdmin, "/api/sondages?taille=100").andExpect(jsonPath("$.contenu", hasSize(13)));
        assertThat(requetesPour("/api/sondages?taille=100")).isEqualTo(avecUn);
        assertThat(requetesPour("/api/sondages?taille=100&derniereVersion=true")).isEqualTo(avecUnDerniere);
    }

    // ------------------------------------------------------------------ Tableau de bord

    @Test
    void tableauDeBordCompteLesLigneesLesGestionnairesEtLesReponses() throws Exception {
        Map<String, Long> ids = lignees();
        repondre(ids.get("A1"), ids.get("A1"), 3);
        repondre(ids.get("C1"), ids.get("C1"), 2);
        repondre(ids.get("C2"), ids.get("C1"), 1);
        gestionnaire("g1@test.com", admin, true);
        Utilisateur g2 = gestionnaire("g2@test.com", admin, true);
        gestionnaire("g3@test.com", admin, true);
        patchAvec(jetonAdmin, "/api/admin/gestionnaires/" + g2.getId() + "/desactiver").andExpect(status().isOk());

        // Une autre entreprise : rien de ses chiffres ne doit apparaître.
        Utilisateur autre = adminActif("autre@test.com");
        String jetonAutre = jeton("autre@test.com");
        long sondageAutre = sondagePublie(jetonAutre, "Autre");
        repondre(sondageAutre, sondageAutre, 5);
        gestionnaire("g4@test.com", autre, true);

        getAvec(jetonAdmin, "/api/admin/tableau-de-bord")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abonnement.formule").value("MENSUEL"))
                .andExpect(jsonPath("$.abonnement.statut").value("ACTIF"))
                .andExpect(jsonPath("$.abonnement.joursRestants").value(30))
                .andExpect(jsonPath("$.abonnement.dateExpiration").isNotEmpty())
                .andExpect(jsonPath("$.gestionnaires.actifs").value(2))
                .andExpect(jsonPath("$.gestionnaires.desactives").value(1))
                // B et D ; A (v1) et C (v2) ; E.
                .andExpect(jsonPath("$.sondages.NON_COMMENCE").value(2))
                .andExpect(jsonPath("$.sondages.EN_COURS").value(2))
                .andExpect(jsonPath("$.sondages.TERMINE").value(1))
                .andExpect(jsonPath("$.nombreTotalReponses").value(6));

        getAvec(jetonAutre, "/api/admin/tableau-de-bord")
                .andExpect(jsonPath("$.gestionnaires.actifs").value(1))
                .andExpect(jsonPath("$.sondages.EN_COURS").value(1))
                .andExpect(jsonPath("$.nombreTotalReponses").value(5));
    }

    @Test
    void tableauDeBordReserveALAdminEtSoumisALAbonnement() throws Exception {
        gestionnaire("g1@test.com", admin, true);
        getAvec(jeton("g1@test.com"), "/api/admin/tableau-de-bord")
                .andExpect(status().isForbidden());
        getAvec(null, "/api/admin/tableau-de-bord").andExpect(status().isUnauthorized());

        admin("expire@test.com", Instant.now().minus(Duration.ofDays(1)));
        getAvec(jeton("expire@test.com"), "/api/admin/tableau-de-bord")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.abonnement.statut").value("EXPIRE"))
                .andExpect(jsonPath("$.sondages.EN_COURS").value(0));

        admin("attente@test.com", null);
        getAvec(jeton("attente@test.com"), "/api/admin/tableau-de-bord")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ABONNEMENT_EN_ATTENTE_PAIEMENT"));
    }

    // ------------------------------------------------------------------ Gestionnaires : filtres

    @Test
    void rechercheEtFiltreActifDesGestionnaires() throws Exception {
        Utilisateur eloise = gestionnaireNomme("eloise.k@test.com", "Éloïse", "Kanté");
        Utilisateur jose = gestionnaireNomme("j.diallo@test.com", "José", "DIALLO");
        Utilisateur awa = gestionnaireNomme("awa.ndiaye@exemple.sn", "Awa", "Ndiaye");
        patchAvec(jetonAdmin, "/api/admin/gestionnaires/" + jose.getId() + "/desactiver").andExpect(status().isOk());
        // Même prénom dans une autre entreprise : jamais renvoyé.
        Utilisateur autre = adminActif("autre@test.com");
        Utilisateur intrus = gestionnaire("eloise@autre.com", autre);
        intrus.setPrenom("Eloise");
        utilisateurRepository.save(intrus);

        assertThat(idsGestionnaires("")).containsExactlyInAnyOrder(eloise.getId(), jose.getId(), awa.getId());
        assertThat(idsGestionnaires("?recherche=ELOISE")).containsExactly(eloise.getId());
        assertThat(idsGestionnaires("?recherche=kante")).containsExactly(eloise.getId());
        assertThat(idsGestionnaires("?recherche=%C3%A9lo%C3%AF")).containsExactly(eloise.getId()); // « éloï »
        assertThat(idsGestionnaires("?recherche=Diallo")).containsExactly(jose.getId());
        assertThat(idsGestionnaires("?recherche=exemple.sn")).containsExactly(awa.getId());
        assertThat(idsGestionnaires("?recherche=%20%20")).hasSize(3); // vide : aucun filtre
        // Les jokers SQL sont cherchés tels quels.
        assertThat(idsGestionnaires("?recherche=%25")).isEmpty();
        assertThat(idsGestionnaires("?recherche=_")).isEmpty();

        assertThat(idsGestionnaires("?actif=false")).containsExactly(jose.getId());
        assertThat(idsGestionnaires("?actif=true")).containsExactlyInAnyOrder(eloise.getId(), awa.getId());
        assertThat(idsGestionnaires("?actif=true&recherche=jose")).isEmpty();
        assertThat(idsGestionnaires("?actif=false&recherche=jos%C3%A9")).containsExactly(jose.getId());

        getAvec(jetonAdmin, "/api/admin/gestionnaires?actif=peut-etre").andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ Paiements

    @Test
    void historiqueDesPaiementsReussis() throws Exception {
        Instant maintenant = Instant.now();
        long ancien = paiement(admin, StatutPaiement.REUSSI, Formule.MENSUEL, maintenant.minus(Duration.ofDays(60)));
        long recent = paiement(admin, StatutPaiement.REUSSI, Formule.ANNUEL, maintenant.minus(Duration.ofDays(1)));
        long milieu = paiement(admin, StatutPaiement.REUSSI, Formule.MENSUEL, maintenant.minus(Duration.ofDays(30)));
        paiement(admin, StatutPaiement.EN_ATTENTE, Formule.ANNUEL, null);
        paiement(admin, StatutPaiement.ECHOUE, Formule.ANNUEL, null);
        Utilisateur autre = adminActif("autre@test.com");
        paiement(autre, StatutPaiement.REUSSI, Formule.MENSUEL, maintenant);

        getAvec(jetonAdmin, "/api/abonnements/paiements")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.contenu[0].id").value(recent))
                .andExpect(jsonPath("$.contenu[0].formule").value("ANNUEL"))
                .andExpect(jsonPath("$.contenu[0].montant").value(1.5))
                .andExpect(jsonPath("$.contenu[0].devise").value("CAD"))
                .andExpect(jsonPath("$.contenu[0].datePaiement").isNotEmpty())
                .andExpect(jsonPath("$.contenu[1].id").value(milieu))
                .andExpect(jsonPath("$.contenu[2].id").value(ancien));

        getAvec(jetonAdmin, "/api/abonnements/paiements?page=1&taille=2")
                .andExpect(jsonPath("$.contenu", hasSize(1)))
                .andExpect(jsonPath("$.contenu[0].id").value(ancien))
                .andExpect(jsonPath("$.totalPages").value(2));
        getAvec(jetonAdmin, "/api/abonnements/paiements?taille=0").andExpect(status().isBadRequest());
    }

    @Test
    void historiqueDesPaiementsDroitsEtAbonnement() throws Exception {
        gestionnaire("g1@test.com", admin, true);
        getAvec(jeton("g1@test.com"), "/api/abonnements/paiements").andExpect(status().isForbidden());

        // Permis abonnement expiré ou en attente de paiement, comme le reste de /api/abonnements/**.
        Utilisateur expire = admin("expire@test.com", Instant.now().minus(Duration.ofDays(1)));
        paiement(expire, StatutPaiement.REUSSI, Formule.MENSUEL, Instant.now().minus(Duration.ofDays(31)));
        getAvec(jeton("expire@test.com"), "/api/abonnements/paiements")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu", hasSize(1)));
        admin("attente@test.com", null);
        getAvec(jeton("attente@test.com"), "/api/abonnements/paiements")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu", hasSize(0)));
    }

    // ------------------------------------------------------------------ Outils

    private long sondageAvecQuestion(String jeton, String titre) throws Exception {
        long id = lire(postAvec(jeton, "/api/sondages", Map.of("titre", titre)).andExpect(status().isCreated()))
                .get("id").asLong();
        long sectionId = lire(postAvec(jeton, "/api/sondages/" + id + "/sections", Map.of("titre", "Section")))
                .at("/sections/0/id").asLong();
        postAvec(jeton, "/api/sondages/" + id + "/sections/" + sectionId + "/questions",
                SondageIntegrationTest.question("Q", "OUI_NON", null)).andExpect(status().isCreated());
        return id;
    }

    private long sondagePublie(String jeton, String titre) throws Exception {
        long id = sondageAvecQuestion(jeton, titre);
        postAvec(jeton, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
        return id;
    }

    private long nouvelleVersion(String jeton, long sondageId) throws Exception {
        return lire(postAvec(jeton, "/api/sondages/" + sondageId + "/nouvelle-version", Map.of())
                .andExpect(status().isCreated())).get("id").asLong();
    }

    /** Réponses vides insérées directement : seuls les comptages sont testés ici. */
    private void repondre(long sondageId, long ligneeId, int nombre) {
        for (int i = 0; i < nombre; i++) {
            jdbcTemplate.update("insert into reponse (sondage_id, lignee_id, date_soumission) values (?, ?, ?)",
                    sondageId, ligneeId, Timestamp.from(Instant.now()));
        }
    }

    private Utilisateur gestionnaireNomme(String courriel, String prenom, String nom) {
        Utilisateur gestionnaire = gestionnaire(courriel, admin);
        gestionnaire.setPrenom(prenom);
        gestionnaire.setNom(nom);
        return utilisateurRepository.save(gestionnaire);
    }

    private List<Long> idsGestionnaires(String filtres) throws Exception {
        // URI déjà encodée : une chaîne serait ré-encodée par MockMvc (%C3%A9 deviendrait %25C3%25A9).
        JsonNode page = lire(mockMvc.perform(get(URI.create("/api/admin/gestionnaires" + filtres))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jetonAdmin))
                .andExpect(status().isOk()));
        List<Long> ids = new ArrayList<>();
        page.get("contenu").forEach(g -> ids.add(g.get("id").asLong()));
        return ids;
    }

    private long paiement(Utilisateur proprietaire, StatutPaiement statut, Formule formule, Instant datePaiement) {
        Abonnement abonnement = abonnementRepository.findByEntrepriseId(proprietaire.getEntreprise().getId())
                .orElseThrow();
        Paiement paiement = new Paiement();
        paiement.setAbonnement(abonnement);
        paiement.setStripePaymentIntentId("pi_test_" + System.nanoTime());
        paiement.setFormule(formule);
        paiement.setMontant(formule == Formule.ANNUEL ? new BigDecimal("1.50") : new BigDecimal("1.00"));
        paiement.setDevise("CAD");
        paiement.setStatut(statut);
        paiement.setDatePaiement(datePaiement);
        return paiementRepository.save(paiement).getId();
    }

    /** Nombre de requêtes SQL préparées pour un GET de l'admin. */
    private long requetesPour(String url) throws Exception {
        Statistics statistiques = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistiques.clear();
        getAvec(jetonAdmin, url).andExpect(status().isOk());
        return statistiques.getPrepareStatementCount();
    }

    private static Map<Long, JsonNode> parId(JsonNode page) {
        Map<Long, JsonNode> lignes = new HashMap<>();
        page.get("contenu").forEach(ligne -> lignes.put(ligne.get("id").asLong(), ligne));
        return lignes;
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
