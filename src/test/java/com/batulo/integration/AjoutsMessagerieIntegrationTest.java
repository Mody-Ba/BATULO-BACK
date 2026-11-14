package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.batulo.service.ExpirationAbonnementService;
import com.batulo.service.model.IntentionStripeModel;
import com.batulo.service.paiement.PaiementService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ajouts pour la messagerie : filtre « lue » des notifications, referenceId et conversationId de chaque type,
 * détail d'une demande d'abonnement, détail d'un envoi de statistiques, noms de schémas OpenAPI distincts.
 */
class AjoutsMessagerieIntegrationTest extends PostgresIntegrationTestBase {

    /** Stripe simulé : l'intention créée est relue comme payée, avec le même montant et les mêmes métadonnées. */
    @MockitoBean
    PaiementService paiementService;
    @Autowired
    ExpirationAbonnementService expirationAbonnementService;

    private Utilisateur sa;
    private Utilisateur admin;
    private Utilisateur g1;
    private String jetonSa;
    private String jetonAdmin;
    private String jetonG1;

    @BeforeEach
    void preparer() throws Exception {
        sa = superAdmin("sa@test.com");
        admin = adminActif("awa@test.com");
        g1 = gestionnaire("g1@test.com", admin, true);
        gestionnaire("g2@test.com", admin, true);
        jetonSa = jeton("sa@test.com");
        jetonAdmin = jeton("awa@test.com");
        jetonG1 = jeton("g1@test.com");
    }

    // ------------------------------------------------------------------ Filtre « lue »

    @Test
    void filtreLueAvecPaginationEtIsolation() throws Exception {
        for (int i = 0; i < 5; i++) {
            notificationEnBase(admin, i < 2);
        }
        notificationEnBase(g1, false);

        JsonNode toutes = lire(getAvec(jetonAdmin, "/api/notifications?taille=2").andExpect(status().isOk()));
        assertThat(toutes.get("totalElements").asLong()).isEqualTo(5);
        assertThat(toutes.get("totalPages").asInt()).isEqualTo(3);

        JsonNode nonLues = lire(getAvec(jetonAdmin, "/api/notifications?lue=false&taille=2"));
        assertThat(nonLues.get("totalElements").asLong()).isEqualTo(3);
        assertThat(nonLues.get("totalPages").asInt()).isEqualTo(2);
        assertThat(nonLues.get("contenu")).hasSize(2);
        assertThat(lire(getAvec(jetonAdmin, "/api/notifications?lue=false&taille=2&page=1")).get("contenu")).hasSize(1);
        lire(getAvec(jetonAdmin, "/api/notifications?lue=false&taille=100")).get("contenu")
                .forEach(n -> assertThat(n.get("lue").asBoolean()).isFalse());

        JsonNode lues = lire(getAvec(jetonAdmin, "/api/notifications?lue=true"));
        assertThat(lues.get("totalElements").asLong()).isEqualTo(2);
        lues.get("contenu").forEach(n -> assertThat(n.get("lue").asBoolean()).isTrue());

        // Chacun ne voit que les siennes.
        getAvec(jetonG1, "/api/notifications?lue=false").andExpect(jsonPath("$.totalElements").value(1));
        getAvec(jetonAdmin, "/api/notifications?lue=peut-etre")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));

        // Une notification antérieure aux nouvelles colonnes sort avec null.
        JsonNode ancienne = toutes.at("/contenu/0");
        assertThat(ancienne.has("referenceId")).isTrue();
        assertThat(ancienne.get("referenceId").isNull()).isTrue();
        assertThat(ancienne.get("conversationId").isNull()).isTrue();
    }

    // ------------------------------------------------------------------ referenceId et conversationId

    @Test
    void assignationEtRetraitDesignentLaLignee() throws Exception {
        long v1 = sondagePublie("Assigné");
        long v2 = nouvelleVersion(v1);
        // Assigner depuis la v2 : l'assignation (et la notification) portent sur la lignée, version 1.
        postAvec(jetonAdmin, "/api/sondages/" + v2 + "/assignations", Map.of("gestionnaireIds", List.of(g1.getId())))
                .andExpect(status().isOk());
        deleteAvec(jetonAdmin, "/api/sondages/" + v2 + "/assignations/" + g1.getId()).andExpect(status().isNoContent());

        JsonNode assigne = derniere(jetonG1, "SONDAGE_ASSIGNE");
        assertThat(assigne.get("referenceId").asLong()).isEqualTo(v1);
        assertThat(assigne.get("conversationId").isNull()).isTrue();
        assertThat(derniere(jetonG1, "SONDAGE_DESASSIGNE").get("referenceId").asLong()).isEqualTo(v1);
    }

    @Test
    void nouveauMessageDesigneLeMessageEtLaConversation() throws Exception {
        // Gestionnaire -> admin.
        long conversation = lire(postAvec(jetonG1, "/api/messagerie/conversations", Map.of())).get("id").asLong();
        long message = lire(postAvec(jetonG1, "/api/messagerie/conversations/" + conversation + "/messages",
                Map.of("contenu", "Bonjour")).andExpect(status().isCreated())).get("id").asLong();
        JsonNode pourAdmin = derniere(jetonAdmin, "NOUVEAU_MESSAGE");
        assertThat(pourAdmin.get("referenceId").asLong()).isEqualTo(message);
        assertThat(pourAdmin.get("conversationId").asLong()).isEqualTo(conversation);

        // Admin -> gestionnaire.
        long reponse = lire(postAvec(jetonAdmin, "/api/messagerie/conversations/" + conversation + "/messages",
                Map.of("contenu", "Bien reçu")).andExpect(status().isCreated())).get("id").asLong();
        JsonNode pourG1 = derniere(jetonG1, "NOUVEAU_MESSAGE");
        assertThat(pourG1.get("referenceId").asLong()).isEqualTo(reponse);
        assertThat(pourG1.get("conversationId").asLong()).isEqualTo(conversation);

        // Admin -> Batulo : tous les super admins.
        long avecBatulo = lire(postAvec(jetonAdmin, "/api/messagerie/conversations",
                Map.of())).get("id").asLong();
        long versBatulo = lire(postAvec(jetonAdmin, "/api/messagerie/conversations/" + avecBatulo + "/messages",
                Map.of("contenu", "Une question")).andExpect(status().isCreated())).get("id").asLong();
        JsonNode pourSa = derniere(jetonSa, "NOUVEAU_MESSAGE");
        assertThat(pourSa.get("referenceId").asLong()).isEqualTo(versBatulo);
        assertThat(pourSa.get("conversationId").asLong()).isEqualTo(avecBatulo);
    }

    @Test
    void statistiquesRecuesDesignentLEnvoiEtLaConversation() throws Exception {
        long envoi = envoiTermine();
        JsonNode notification = derniere(jetonAdmin, "STATISTIQUES_RECUES");
        assertThat(notification.get("referenceId").asLong()).isEqualTo(envoi);
        assertThat(notification.get("conversationId").asLong()).isEqualTo(conversationAdminGestionnaire());
    }

    @Test
    void demandesDesignentLaDemandeEtLaConversationAvecBatulo() throws Exception {
        long demande = lire(postAvec(jetonAdmin, "/api/abonnements/demandes",
                Map.of("formuleSouhaitee", "ANNUEL", "message", "Plus d'enquêtes")).andExpect(status().isCreated()))
                .get("id").asLong();
        Long conversation = jdbcTemplate.queryForObject(
                "select id from conversation where admin_id = ? and gestionnaire_id is null", Long.class,
                admin.getId());

        JsonNode recue = derniere(jetonSa, "DEMANDE_ABONNEMENT");
        assertThat(recue.get("referenceId").asLong()).isEqualTo(demande);
        assertThat(recue.get("conversationId").asLong()).isEqualTo(conversation);

        patchJson(jetonSa, "/api/super-admin/demandes-abonnement/" + demande, Map.of("statut", "TRAITEE"))
                .andExpect(status().isOk());
        JsonNode traitee = derniere(jetonAdmin, "DEMANDE_ABONNEMENT_TRAITEE");
        assertThat(traitee.get("referenceId").asLong()).isEqualTo(demande);
        assertThat(traitee.get("conversationId").asLong()).isEqualTo(conversation);
    }

    @Test
    void inscriptionEtPaiementDesignentLAbonnement() throws Exception {
        postAvec(null, "/api/auth/inscription", Map.of("prenom", "Fatou", "nom", "Sow", "courriel", "fatou@test.com",
                "motDePasse", MOT_DE_PASSE, "confirmationMotDePasse", MOT_DE_PASSE, "nomEntreprise", "Sow SARL"))
                .andExpect(status().isCreated());
        Long abonnement = jdbcTemplate.queryForObject("select a.id from abonnement a join utilisateur u "
                + "on u.entreprise_id = a.entreprise_id where u.courriel = 'fatou@test.com'", Long.class);
        assertThat(derniere(jetonSa, "NOUVELLE_INSCRIPTION").get("referenceId").asLong()).isEqualTo(abonnement);

        simulerStripe();
        String jetonFatou = jeton("fatou@test.com");
        String intention = lire(postAvec(jetonFatou, "/api/abonnements/paiement/intention", Map.of("formule", "MENSUEL"))
                .andExpect(status().isCreated())).get("paymentIntentId").asText();
        postAvec(jetonFatou, "/api/abonnements/paiement/confirmer", Map.of("paymentIntentId", intention))
                .andExpect(status().isOk());
        JsonNode paiement = derniere(jetonSa, "PAIEMENT_RECU");
        assertThat(paiement.get("referenceId").asLong()).isEqualTo(abonnement);
        assertThat(paiement.get("conversationId").isNull()).isTrue();
    }

    @Test
    void expirationDesigneLAbonnement() throws Exception {
        Long abonnement = abonnementDe(admin);
        jdbcTemplate.update("update abonnement set date_expiration = ? where id = ?",
                Timestamp.from(Instant.now().plus(Duration.ofDays(3))), abonnement);
        expirationAbonnementService.verifierAbonnements();
        assertThat(derniere(jetonAdmin, "ABONNEMENT_EXPIRE_BIENTOT").get("referenceId").asLong()).isEqualTo(abonnement);
        assertThat(derniere(jetonSa, "ABONNEMENT_EXPIRE_BIENTOT").get("referenceId").asLong()).isEqualTo(abonnement);

        jdbcTemplate.update("update abonnement set date_expiration = ? where id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofHours(1))), abonnement);
        expirationAbonnementService.verifierAbonnements();
        assertThat(derniere(jetonAdmin, "ABONNEMENT_EXPIRE").get("referenceId").asLong()).isEqualTo(abonnement);
        assertThat(derniere(jetonSa, "ABONNEMENT_EXPIRE").get("referenceId").asLong()).isEqualTo(abonnement);
    }

    // ------------------------------------------------------------------ Détail d'une demande

    @Test
    void detailDUneDemandeReserveAuSuperAdmin() throws Exception {
        long demande = lire(postAvec(jetonAdmin, "/api/abonnements/demandes",
                Map.of("formuleSouhaitee", "ANNUEL", "message", "Trois enquêtes"))).get("id").asLong();

        getAvec(jetonSa, "/api/super-admin/demandes-abonnement/" + demande)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(demande))
                .andExpect(jsonPath("$.entreprise").value("Entreprise de awa@test.com"))
                .andExpect(jsonPath("$.formuleSouhaitee").value("ANNUEL"))
                .andExpect(jsonPath("$.formuleActuelle").value("MENSUEL"))
                .andExpect(jsonPath("$.statutAbonnement").value("ACTIF"))
                .andExpect(jsonPath("$.message").value("Trois enquêtes"))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));
        getAvec(jetonSa, "/api/super-admin/demandes-abonnement/999999")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DEMANDE_INTROUVABLE"));
        getAvec(jetonAdmin, "/api/super-admin/demandes-abonnement/" + demande).andExpect(status().isForbidden());
        getAvec(jetonG1, "/api/super-admin/demandes-abonnement/" + demande).andExpect(status().isForbidden());
        getAvec(null, "/api/super-admin/demandes-abonnement/" + demande).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ Détail d'un envoi

    @Test
    void detailDUnEnvoiPourSesDeuxParticipants() throws Exception {
        long envoi = envoiTermine();
        String url = "/api/envois-statistiques/" + envoi;

        for (String jeton : List.of(jetonAdmin, jetonG1)) {
            getAvec(jeton, url)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(envoi))
                    .andExpect(jsonPath("$.gestionnaireId").value(g1.getId()))
                    .andExpect(jsonPath("$.expediteurNom").value("Prénom Nom"))
                    .andExpect(jsonPath("$.commentaire").value("À regarder"))
                    .andExpect(jsonPath("$.sondageTitre").value("Envoyé"))
                    .andExpect(jsonPath("$.lignee").value(true))
                    .andExpect(jsonPath("$.du").value("2026-01-01"))
                    .andExpect(jsonPath("$.dateEnvoi").isNotEmpty())
                    .andExpect(jsonPath("$.consulteLe").doesNotExist());
        }
        // Le détail ne marque pas l'envoi comme consulté.
        assertThat(jdbcTemplate.queryForObject("select consulte_le from envoi_statistique where id = ?",
                Timestamp.class, envoi)).isNull();

        // Autre gestionnaire, autre admin : 404 ; super admin : 403 (rôle).
        getAvec(jeton("g2@test.com"), url)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ENVOI_INTROUVABLE"));
        adminActif("autre@test.com");
        getAvec(jeton("autre@test.com"), url).andExpect(status().isNotFound());
        getAvec(jetonSa, url).andExpect(status().isForbidden());
        getAvec(jetonAdmin, "/api/envois-statistiques/999999").andExpect(status().isNotFound());

        // Expéditeur supprimé : le nom reste, l'admin voit toujours l'envoi.
        jdbcTemplate.update("delete from utilisateur where id = ?", g1.getId());
        getAvec(jetonAdmin, url)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gestionnaireId").doesNotExist())
                .andExpect(jsonPath("$.expediteurNom").value("Prénom Nom"));
    }

    // ------------------------------------------------------------------ OpenAPI

    @Test
    void schemasDistinctsPourLesCellulesEtLAbonnementDeLaListeDesAdmins() throws Exception {
        JsonNode schemas = lire(mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()))
                .at("/components/schemas");
        assertThat(schemas.at("/CelluleCroisement/properties").has("pourcentageLigne")).isTrue();
        assertThat(schemas.at("/CelluleCroisement/properties").has("petitEffectif")).isTrue();
        assertThat(schemas.at("/Cellule/properties").has("pourcentage")).isTrue();
        assertThat(schemas.at("/Cellule/properties").has("pourcentageLigne")).isFalse();
        assertThat(schemas.at("/LigneMatrice/properties/cellules/items/$ref").asText()).endsWith("/Cellule");
        assertThat(schemas.at("/Ligne/properties/cellules/items/$ref").asText()).endsWith("/CelluleCroisement");

        assertThat(schemas.has("AbonnementLigneAdmin")).isTrue();
        assertThat(schemas.at("/AdminLigneResponse/properties/abonnement/$ref").asText())
                .endsWith("/AbonnementLigneAdmin");
        // Le JSON ne change pas : le champ s'appelle toujours « abonnement ».
        getAvec(jetonSa, "/api/super-admin/admins").andExpect(jsonPath("$.contenu[0].abonnement.statut").value("ACTIF"));
    }

    // ------------------------------------------------------------------ Outils

    private void notificationEnBase(Utilisateur destinataire, boolean lue) {
        jdbcTemplate.update("insert into notification (destinataire_id, type, titre, message, lue, date_creation) "
                + "values (?, 'NOUVELLE_INSCRIPTION', 't', 'm', ?, ?)", destinataire.getId(), lue,
                Timestamp.from(Instant.now()));
    }

    /** La notification la plus récente de ce type pour le compte. */
    private JsonNode derniere(String jeton, String type) throws Exception {
        JsonNode page = lire(getAvec(jeton, "/api/notifications?taille=100").andExpect(status().isOk()));
        for (JsonNode notification : page.get("contenu")) {
            if (notification.get("type").asText().equals(type)) {
                return notification;
            }
        }
        throw new AssertionError("Aucune notification " + type + " ; reçues : " + types(page));
    }

    private static List<String> types(JsonNode page) {
        List<String> types = new ArrayList<>();
        page.get("contenu").forEach(n -> types.add(n.get("type").asText()));
        return types;
    }

    private long sondagePublie(String titre) throws Exception {
        long id = lire(postAvec(jetonAdmin, "/api/sondages", Map.of("titre", titre)).andExpect(status().isCreated()))
                .get("id").asLong();
        long section = lire(postAvec(jetonAdmin, "/api/sondages/" + id + "/sections", Map.of("titre", "S")))
                .at("/sections/0/id").asLong();
        postAvec(jetonAdmin, "/api/sondages/" + id + "/sections/" + section + "/questions",
                SondageIntegrationTest.question("Q", "OUI_NON", null)).andExpect(status().isCreated());
        postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
        return id;
    }

    private long nouvelleVersion(long sondageId) throws Exception {
        return lire(postAvec(jetonAdmin, "/api/sondages/" + sondageId + "/nouvelle-version", Map.of())
                .andExpect(status().isCreated())).get("id").asLong();
    }

    /** Sondage assigné à g1, fermé (TERMINE), dont g1 envoie les statistiques à l'admin. */
    private long envoiTermine() throws Exception {
        long id = sondagePublie("Envoyé");
        postAvec(jetonAdmin, "/api/sondages/" + id + "/assignations", Map.of("gestionnaireIds", List.of(g1.getId())))
                .andExpect(status().isOk());
        postAvec(jetonAdmin, "/api/sondages/" + id + "/fermer", Map.of()).andExpect(status().isOk());
        return lire(postAvec(jetonG1, "/api/sondages/" + id + "/envois-statistiques",
                Map.of("commentaire", "À regarder", "lignee", true, "du", "2026-01-01"))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long conversationAdminGestionnaire() {
        return jdbcTemplate.queryForObject("select id from conversation where admin_id = ? and gestionnaire_id = ?",
                Long.class, admin.getId(), g1.getId());
    }

    private Long abonnementDe(Utilisateur compte) {
        return jdbcTemplate.queryForObject("select id from abonnement where entreprise_id = ?", Long.class,
                compte.getEntreprise().getId());
    }

    private void simulerStripe() {
        AtomicReference<IntentionStripeModel> creee = new AtomicReference<>();
        when(paiementService.clePublique()).thenReturn("pk_test");
        when(paiementService.creerIntention(anyLong(), anyString(), anyString(), anyMap())).thenAnswer(appel -> {
            IntentionStripeModel intention = new IntentionStripeModel("pi_test", "secret", "requires_payment_method",
                    appel.getArgument(0), appel.getArgument(1), appel.getArgument(3));
            creee.set(intention);
            return intention;
        });
        when(paiementService.recupererIntention(any())).thenAnswer(appel -> {
            IntentionStripeModel i = creee.get();
            return new IntentionStripeModel(i.id(), i.clientSecret(), "succeeded", i.montantCentimes(), i.devise(),
                    i.metadonnees());
        });
    }

    private ResultActions patchJson(String jeton, String url, Object corps) throws Exception {
        return mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(url)
                .header("Authorization", "Bearer " + jeton)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json(corps)));
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
