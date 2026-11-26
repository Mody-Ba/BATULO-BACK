package com.batulo.integration;

import com.batulo.service.model.IntentionStripeModel;
import com.batulo.service.model.SessionMoosylModel;
import com.batulo.service.paiement.MoosylClient;
import com.batulo.service.paiement.PaiementService;
import com.batulo.service.paiement.SignatureWebhookMoosyl;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Achat de sondages à l'unité : achat par carte et par mobile (prestataires simulés), crédit une seule fois,
 * consommation à la publication d'une lignée hors abonnement, accès et collecte d'un admin sans abonnement.
 * Prix du profil de test : 0.50 CAD et 20 MRU par sondage.
 */
class AchatSondagesIntegrationTest extends PostgresIntegrationTestBase {

    private static final String SECRET_MOOSYL = "secret-webhook-moosyl-de-test";

    @MockitoBean
    PaiementService paiementService;
    @MockitoBean
    MoosylClient moosylClient;

    private String jetonAdmin;

    @BeforeEach
    void preparer() throws Exception {
        superAdmin("sa@test.com");
        admin("awa@test.com", null);
        jetonAdmin = jeton("awa@test.com");
        simulerStripe();
        when(moosylClient.creerSession(anyString(), anyLong(), any(), anyString(), anyInt())).thenAnswer(appel ->
                new SessionMoosylModel("cs_" + appel.getArgument(0), "https://payments.moosyl.com/checkout/cs_"
                        + appel.getArgument(0), Instant.now().plus(Duration.ofMinutes(30))));
        when(moosylClient.statutSession(anyString())).thenReturn(Optional.of("open"));
    }

    // ------------------------------------------------------------------ Vitrine et achat

    @Test
    void formulesDonnentLePrixUnitaireSansCasserLesAbonnements() throws Exception {
        JsonNode formules = lire(mockMvc.perform(get("/api/abonnements/formules")).andExpect(status().isOk()));
        assertThat(formules.size()).isEqualTo(3);
        assertThat(formules.get(0).get("formule").asText()).isEqualTo("MENSUEL");
        assertThat(formules.get(0).get("type").asText()).isEqualTo("ABONNEMENT");
        assertThat(formules.get(0).get("prix").decimalValue()).isEqualByComparingTo("1.00");
        assertThat(formules.get(1).get("formule").asText()).isEqualTo("ANNUEL");
        JsonNode achat = formules.get(2);
        assertThat(achat.get("type").asText()).isEqualTo("ACHAT_SONDAGES");
        assertThat(achat.get("formule").isNull()).isTrue();
        assertThat(achat.get("prix").decimalValue()).isEqualByComparingTo("0.50");
        assertThat(achat.get("devise").asText()).isEqualTo("CAD");
        assertThat(achat.get("prixMobile").decimalValue()).isEqualByComparingTo("20");
        assertThat(achat.get("deviseMobile").asText()).isEqualTo("MRU");
        assertThat(achat.get("quantiteMin").asInt()).isEqualTo(1);
        assertThat(achat.get("quantiteMax").asInt()).isEqualTo(1000);
    }

    @Test
    void achatParCarteCrediteUneSeuleFois() throws Exception {
        JsonNode intention = lire(postAvec(jetonAdmin, "/api/abonnements/achat-sondages/intention",
                Map.of("quantite", 5)).andExpect(status().isCreated()));
        assertThat(intention.get("type").asText()).isEqualTo("ACHAT_SONDAGES");
        assertThat(intention.get("quantite").asInt()).isEqualTo(5);
        assertThat(intention.get("formule").isNull()).isTrue();
        assertThat(intention.get("montant").decimalValue()).isEqualByComparingTo("2.50");
        // Montant calculé par le serveur : 5 × 0,50 $ = 250 centimes ; type et quantité dans les métadonnées Stripe.
        verify(paiementService).creerIntention(eq(250L), eq("cad"), anyString(),
                eq(Map.of("entrepriseId", entrepriseId("awa@test.com").toString(),
                        "abonnementId", abonnementId("awa@test.com").toString(),
                        "type", "ACHAT_SONDAGES", "quantite", "5")));
        // Rien n'est crédité avant la confirmation.
        getAvec(jetonAdmin, "/api/abonnements/credits").andExpect(jsonPath("$.solde").value(0))
                .andExpect(jsonPath("$.compteCredits").value(false));

        String pi = intention.get("paymentIntentId").asText();
        postAvec(jetonAdmin, "/api/abonnements/paiement/confirmer", Map.of("paymentIntentId", pi))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ACHAT_SONDAGES"))
                .andExpect(jsonPath("$.quantite").value(5))
                .andExpect(jsonPath("$.credits.solde").value(5))
                .andExpect(jsonPath("$.abonnement.statut").value("EN_ATTENTE_PAIEMENT"));
        // Confirmation répétée : pas de second crédit.
        postAvec(jetonAdmin, "/api/abonnements/paiement/confirmer", Map.of("paymentIntentId", pi))
                .andExpect(status().isOk()).andExpect(jsonPath("$.credits.solde").value(5))
                .andExpect(jsonPath("$.message").value(
                        "Ce paiement avait déjà été confirmé : les sondages ne sont pas crédités une seconde fois."));
        verify(emailService, times(1)).envoyerConfirmationAchatSondages(eq("awa@test.com"), anyString(), eq(5),
                eq(new BigDecimal("2.50")), eq("CAD"), eq(5));

        // Un second achat s'ajoute au premier ; l'abonnement n'est jamais touché.
        String pi2 = lire(postAvec(jetonAdmin, "/api/abonnements/achat-sondages/intention", Map.of("quantite", 2))
                .andExpect(status().isCreated())).get("paymentIntentId").asText();
        postAvec(jetonAdmin, "/api/abonnements/paiement/confirmer", Map.of("paymentIntentId", pi2))
                .andExpect(jsonPath("$.credits.solde").value(7));
        getAvec(jetonAdmin, "/api/abonnements/moi")
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE_PAIEMENT"))
                .andExpect(jsonPath("$.formule").isEmpty())
                .andExpect(jsonPath("$.dateExpiration").isEmpty())
                .andExpect(jsonPath("$.credits.solde").value(7))
                .andExpect(jsonPath("$.credits.totalAchete").value(7));

        // Historique commun aux abonnements : type et quantité.
        getAvec(jetonAdmin, "/api/abonnements/paiements")
                .andExpect(jsonPath("$.contenu.length()").value(2))
                .andExpect(jsonPath("$.contenu[0].type").value("ACHAT_SONDAGES"))
                .andExpect(jsonPath("$.contenu[0].quantite").value(2))
                .andExpect(jsonPath("$.contenu[0].fournisseur").value("STRIPE"));
    }

    @Test
    void quantiteHorsLimitesRefusee() throws Exception {
        for (int quantite : new int[]{0, 1001, -3}) {
            postAvec(jetonAdmin, "/api/abonnements/achat-sondages/intention", Map.of("quantite", quantite))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QUANTITE_INVALIDE"));
            postAvec(jetonAdmin, "/api/abonnements/achat-sondages/paiement-mobile", Map.of("quantite", quantite))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QUANTITE_INVALIDE"));
        }
        postAvec(jetonAdmin, "/api/abonnements/achat-sondages/intention", Map.of()).andExpect(status().isBadRequest());
        // Les bornes passent.
        postAvec(jetonAdmin, "/api/abonnements/achat-sondages/intention", Map.of("quantite", 1))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.montant").value(0.50));
        postAvec(jetonAdmin, "/api/abonnements/achat-sondages/intention", Map.of("quantite", 1000))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.montant").value(500.00));
        assertThat(jdbcTemplate.queryForObject("select count(*) from paiement", Long.class)).isEqualTo(2);
    }

    @Test
    void achatParMobileWebhookEnDoubleCrediteUneSeuleFois() throws Exception {
        JsonNode paiement = lire(postAvec(jetonAdmin, "/api/abonnements/achat-sondages/paiement-mobile",
                Map.of("quantite", 3, "telephone", "22222222")).andExpect(status().isCreated()));
        assertThat(paiement.get("type").asText()).isEqualTo("ACHAT_SONDAGES");
        assertThat(paiement.get("quantite").asInt()).isEqualTo(3);
        assertThat(paiement.get("montant").decimalValue()).isEqualByComparingTo("60");
        String reference = paiement.get("reference").asText();
        verify(moosylClient).creerSession(eq(reference), eq(60L), eq("22222222"), anyString(), eq(30));

        // Montant différent : rien n'est crédité.
        webhook(reference, "completed", 20).andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("IGNORE"));
        assertThat(solde("awa@test.com")).isZero();

        webhook(reference, "completed", 60).andExpect(status().isOk()).andExpect(jsonPath("$.resultat").value("TRAITE"));
        webhook(reference, "completed", 60).andExpect(status().isOk());
        assertThat(solde("awa@test.com")).isEqualTo(3);
        // La relecture du statut ne recrédite pas non plus.
        when(moosylClient.statutSession(anyString())).thenReturn(Optional.of("completed"));
        getAvec(jetonAdmin, "/api/abonnements/paiement-mobile/" + reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REUSSI"))
                .andExpect(jsonPath("$.type").value("ACHAT_SONDAGES"))
                .andExpect(jsonPath("$.quantite").value(3))
                .andExpect(jsonPath("$.credits.solde").value(3));
        assertThat(solde("awa@test.com")).isEqualTo(3);
        verify(emailService, times(1)).envoyerConfirmationAchatSondages(eq("awa@test.com"), anyString(), eq(3),
                eq(new BigDecimal("60.00")), eq("MRU"), eq(3));
        assertThat(jdbcTemplate.queryForObject("select count(*) from notification where type = 'PAIEMENT_RECU'",
                Long.class)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ Consommation à la publication

    @Test
    void publicationAvecCreditPuisNouvelleVersionSansDebitPuisRefusASolde0() throws Exception {
        crediter("awa@test.com", 1);
        long v1 = sondageComplet(jetonAdmin, "Enquête");
        postAvec(jetonAdmin, "/api/sondages/" + v1 + "/publier", Map.of()).andExpect(status().isOk());
        assertThat(solde("awa@test.com")).isZero();
        assertThat(jdbcTemplate.queryForObject("select total_utilise from credit_sondage", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select credit_utilise_le from sondage where id = ?", Timestamp.class,
                v1)).isNotNull();

        // Nouvelle version d'une lignée payée : aucun débit, même solde à 0.
        long v2 = lire(postAvec(jetonAdmin, "/api/sondages/" + v1 + "/nouvelle-version", Map.of())
                .andExpect(status().isCreated())).get("id").asLong();
        postAvec(jetonAdmin, "/api/sondages/" + v2 + "/publier", Map.of()).andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject("select total_utilise from credit_sondage", Integer.class)).isEqualTo(1);

        // Duplication : brouillon gratuit, mais nouvelle lignée : sa publication exige un crédit.
        long copie = lire(postAvec(jetonAdmin, "/api/sondages/" + v1 + "/dupliquer", Map.of())
                .andExpect(status().isCreated())).get("id").asLong();
        assertThat(jdbcTemplate.queryForObject("select credit_utilise_le from sondage where id = ?", Timestamp.class,
                copie)).isNull();
        postAvec(jetonAdmin, "/api/sondages/" + copie + "/publier", Map.of())
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("CREDITS_EPUISES"))
                .andExpect(jsonPath("$.message").value("Vous n'avez plus de sondages disponibles : achetez des "
                        + "sondages ou abonnez-vous pour publier ce sondage."));
        // Refus : la publication est entièrement annulée.
        getAvec(jetonAdmin, "/api/sondages/" + copie).andExpect(jsonPath("$.publie").value(false));
        assertThat(jdbcTemplate.queryForObject("select credit_utilise_le from sondage where id = ?", Timestamp.class,
                copie)).isNull();
        assertThat(solde("awa@test.com")).isZero();
    }

    @Test
    void abonnePublieSansDebit() throws Exception {
        adminActif("abonne@test.com");
        String jeton = jeton("abonne@test.com");
        crediter("abonne@test.com", 2);
        long id = sondageComplet(jeton, "Enquête");
        postAvec(jeton, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
        assertThat(solde("abonne@test.com")).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("select credit_utilise_le from sondage where id = ?", Timestamp.class,
                id)).isNull();

        // Abonné sans aucun crédit : publication illimitée.
        adminActif("abonne2@test.com");
        String jeton2 = jeton("abonne2@test.com");
        for (int i = 0; i < 3; i++) {
            postAvec(jeton2, "/api/sondages/" + sondageComplet(jeton2, "S" + i) + "/publier", Map.of())
                    .andExpect(status().isOk());
        }
    }

    @Test
    void lignéeDUnAncienAbonnementPayeeUneSeuleFoisAprèsExpiration() throws Exception {
        adminActif("ancien@test.com");
        String jeton = jeton("ancien@test.com");
        long v1 = sondageComplet(jeton, "Enquête");
        postAvec(jeton, "/api/sondages/" + v1 + "/publier", Map.of()).andExpect(status().isOk());
        String lien = jetonPublic(jeton, v1);
        // L'abonnement expire, puis l'admin achète 2 sondages.
        jdbcTemplate.update("update abonnement set date_expiration = ? where entreprise_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(1))), entrepriseId("ancien@test.com"));
        crediter("ancien@test.com", 2);
        jeton = jeton("ancien@test.com");
        // Ses anciens sondages restent consultables, mais leur collecte ne rouvre pas.
        getAvec(jeton, "/api/sondages/" + v1).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/sondages/" + lien))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("SONDAGE_FERME"));

        // Une nouvelle version de cette lignée utilise un crédit, une seule fois ; la lignée collecte à nouveau.
        long v2 = lire(postAvec(jeton, "/api/sondages/" + v1 + "/nouvelle-version", Map.of())
                .andExpect(status().isCreated())).get("id").asLong();
        postAvec(jeton, "/api/sondages/" + v2 + "/publier", Map.of()).andExpect(status().isOk());
        assertThat(solde("ancien@test.com")).isEqualTo(1);
        mockMvc.perform(get("/api/public/sondages/" + lien)).andExpect(status().isOk());
        long v3 = lire(postAvec(jeton, "/api/sondages/" + v2 + "/nouvelle-version", Map.of())
                .andExpect(status().isCreated())).get("id").asLong();
        postAvec(jeton, "/api/sondages/" + v3 + "/publier", Map.of()).andExpect(status().isOk());
        assertThat(solde("ancien@test.com")).isEqualTo(1);
    }

    @Test
    void deuxPublicationsSimultaneesAvecUnSeulCredit() throws Exception {
        crediter("awa@test.com", 1);
        List<Long> brouillons = List.of(sondageComplet(jetonAdmin, "A"), sondageComplet(jetonAdmin, "B"));
        ExecutorService fils = Executors.newFixedThreadPool(2);
        CountDownLatch depart = new CountDownLatch(1);
        try {
            List<Future<Integer>> resultats = new ArrayList<>();
            for (long id : brouillons) {
                Callable<Integer> publication = () -> {
                    depart.await();
                    return postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of())
                            .andReturn().getResponse().getStatus();
                };
                resultats.add(fils.submit(publication));
            }
            depart.countDown();
            List<Integer> statuts = new ArrayList<>();
            for (Future<Integer> resultat : resultats) {
                statuts.add(resultat.get());
            }
            assertThat(statuts).containsExactlyInAnyOrder(200, 402);
        } finally {
            fils.shutdownNow();
        }
        assertThat(solde("awa@test.com")).isZero();
        assertThat(jdbcTemplate.queryForObject("select total_utilise from credit_sondage", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from sondage where date_publication is not null", Long.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select count(*) from sondage where credit_utilise_le is not null",
                Long.class)).isEqualTo(1);
    }

    // ------------------------------------------------------------------ Accès sans abonnement

    @Test
    void adminSansAbonnementAvecCreditsUtiliseLApplication() throws Exception {
        // Sans abonnement ni crédit : bloqué, avec un message qui propose l'achat.
        getAvec(jetonAdmin, "/api/sondages").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ABONNEMENT_EN_ATTENTE_PAIEMENT"));

        crediter("awa@test.com", 1);
        getAvec(jetonAdmin, "/api/auth/moi")
                .andExpect(jsonPath("$.statutAbonnement").value("EN_ATTENTE_PAIEMENT"))
                .andExpect(jsonPath("$.credits.solde").value(1))
                .andExpect(jsonPath("$.credits.compteCredits").value(true));
        long id = sondageComplet(jetonAdmin, "Enquête");
        postAvec(jetonAdmin, "/api/sondages/" + id + "/publier", Map.of()).andExpect(status().isOk());
        String lien = jetonPublic(jetonAdmin, id);

        // Solde à 0 : tout reste ouvert (gestionnaires compris), seule une nouvelle lignée est refusée.
        postAvec(jetonAdmin, "/api/admin/gestionnaires", Map.of("prenom", "Moussa", "nom", "Ba",
                "courriel", "moussa@test.com")).andExpect(status().isCreated());
        long autre = sondageComplet(jetonAdmin, "Autre");
        postAvec(jetonAdmin, "/api/sondages/" + autre + "/publier", Map.of())
                .andExpect(status().isPaymentRequired()).andExpect(jsonPath("$.code").value("CREDITS_EPUISES"));

        // Le sondage publié avec un crédit reçoit des réponses, et ses statistiques et exports restent ouverts.
        JsonNode formulaire = lire(mockMvc.perform(get("/api/public/sondages/" + lien)).andExpect(status().isOk()));
        long questionId = formulaire.at("/sections/0/questions/0/id").asLong();
        long echelleId = formulaire.at("/sections/0/questions/1/id").asLong();
        mockMvc.perform(post("/api/public/sondages/" + lien + "/reponses").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("jetonRepondant", UUID.randomUUID().toString(), "reponses", List.of(
                                Map.of("questionId", questionId, "valeur", "kaolack"),
                                Map.of("questionId", echelleId, "valeur", 7))))))
                .andExpect(status().isCreated());
        getAvec(jetonAdmin, "/api/sondages/" + id + "/statistiques").andExpect(status().isOk())
                .andExpect(jsonPath("$.resume.nombreReponses").value(1));
        getAvec(jetonAdmin, "/api/sondages/" + id + "/export/reponses?format=CSV").andExpect(status().isOk());
        getAvec(jetonAdmin, "/api/sondages/" + id + "/collecte").andExpect(status().isOk())
                .andExpect(jsonPath("$.collecteOuverte").value(true));

        // Abonnement expiré + compte de crédits : accès complet aussi (pas de lecture seule).
        jdbcTemplate.update("update abonnement set date_expiration = ?, formule = 'MENSUEL' where entreprise_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(2))), entrepriseId("awa@test.com"));
        putAvec(jetonAdmin, "/api/sondages/" + autre, Map.of("titre", "Autre (modifié)")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ Inscription et super admin

    @Test
    void inscriptionAvecOffreChoisie() throws Exception {
        Map<String, Object> base = Map.of("prenom", "Fatou", "nom", "Sow", "courriel", "fatou@test.com",
                "motDePasse", MOT_DE_PASSE, "confirmationMotDePasse", MOT_DE_PASSE);
        Map<String, Object> sansNombre = new java.util.HashMap<>(base);
        sansNombre.put("offre", "ACHAT_SONDAGES");
        inscrire(sansNombre).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("QUANTITE_INVALIDE"));

        Map<String, Object> achat = new java.util.HashMap<>(sansNombre);
        achat.put("nombreSondages", 10);
        inscrire(achat).andExpect(status().isCreated())
                .andExpect(jsonPath("$.offre").value("ACHAT_SONDAGES"))
                .andExpect(jsonPath("$.nombreSondages").value(10))
                .andExpect(jsonPath("$.statutAbonnement").value("EN_ATTENTE_PAIEMENT"));
        getAvec(jeton("fatou@test.com"), "/api/abonnements/moi")
                .andExpect(jsonPath("$.offreChoisie").value("ACHAT_SONDAGES"))
                .andExpect(jsonPath("$.nombreSondagesChoisi").value(10));

        Map<String, Object> annuel = new java.util.HashMap<>(base);
        annuel.put("courriel", "ali@test.com");
        annuel.put("offre", "ANNUEL");
        annuel.put("nombreSondages", 5);
        inscrire(annuel).andExpect(status().isCreated()).andExpect(jsonPath("$.offre").value("ANNUEL"))
                .andExpect(jsonPath("$.nombreSondages").isEmpty());
        // Sans offre : comme avant.
        Map<String, Object> aucune = new java.util.HashMap<>(base);
        aucune.put("courriel", "sans@test.com");
        inscrire(aucune).andExpect(status().isCreated()).andExpect(jsonPath("$.offre").isEmpty());
    }

    @Test
    void superAdminVoitSoldeHistoriqueEtRevenus() throws Exception {
        String pi = lire(postAvec(jetonAdmin, "/api/abonnements/achat-sondages/intention", Map.of("quantite", 4))
                .andExpect(status().isCreated())).get("paymentIntentId").asText();
        postAvec(jetonAdmin, "/api/abonnements/paiement/confirmer", Map.of("paymentIntentId", pi))
                .andExpect(status().isOk());
        String reference = lire(postAvec(jetonAdmin, "/api/abonnements/achat-sondages/paiement-mobile",
                Map.of("quantite", 2)).andExpect(status().isCreated())).get("reference").asText();
        webhook(reference, "completed", 40).andExpect(status().isOk());
        // Un abonnement payé en mobile, pour les revenus.
        String abonnement = lire(postAvec(jetonAdmin, "/api/abonnements/paiement-mobile", Map.of("formule", "MENSUEL"))
                .andExpect(status().isCreated())).get("reference").asText();
        webhook(abonnement, "completed", 40).andExpect(status().isOk());

        String jetonSa = jeton("sa@test.com");
        Long adminId = utilisateurRepository.findByCourriel("awa@test.com").orElseThrow().getId();
        getAvec(jetonSa, "/api/super-admin/admins/" + adminId).andExpect(status().isOk())
                .andExpect(jsonPath("$.credits.solde").value(6))
                .andExpect(jsonPath("$.credits.totalAchete").value(6))
                .andExpect(jsonPath("$.abonnement.statut").value("ACTIF"));
        getAvec(jetonSa, "/api/super-admin/admins/" + adminId + "/achats-sondages").andExpect(status().isOk())
                .andExpect(jsonPath("$.contenu.length()").value(2))
                .andExpect(jsonPath("$.contenu[0].quantite").value(2))
                .andExpect(jsonPath("$.contenu[0].fournisseur").value("MOOSYL"))
                .andExpect(jsonPath("$.contenu[1].quantite").value(4));
        getAvec(jetonSa, "/api/super-admin/admins").andExpect(jsonPath("$.contenu[0].soldeCredits").value(6));
        // Lecture seule : l'admin n'y a pas accès.
        getAvec(jetonAdmin, "/api/super-admin/admins/" + adminId + "/achats-sondages")
                .andExpect(status().isForbidden());

        JsonNode revenus = lire(getAvec(jetonSa, "/api/super-admin/tableau-de-bord").andExpect(status().isOk()))
                .get("revenus");
        assertThat(revenus.get("sondagesVendus").asLong()).isEqualTo(6);
        assertThat(revenus.get("lignes").size()).isEqualTo(3);
        assertThat(ligne(revenus, "ABONNEMENT", "MRU").get("montant").decimalValue()).isEqualByComparingTo("40");
        assertThat(ligne(revenus, "ACHAT_SONDAGES", "CAD").get("montant").decimalValue()).isEqualByComparingTo("2.00");
        assertThat(ligne(revenus, "ACHAT_SONDAGES", "MRU").get("montant").decimalValue()).isEqualByComparingTo("40");
        assertThat(ligne(revenus, "ACHAT_SONDAGES", "MRU").get("nombrePaiements").asLong()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ Outils

    private void simulerStripe() {
        AtomicReference<Integer> compteur = new AtomicReference<>(0);
        Map<String, IntentionStripeModel> creees = new java.util.concurrent.ConcurrentHashMap<>();
        when(paiementService.clePublique()).thenReturn("pk_test");
        when(paiementService.creerIntention(anyLong(), anyString(), anyString(), anyMap())).thenAnswer(appel -> {
            compteur.set(compteur.get() + 1);
            IntentionStripeModel intention = new IntentionStripeModel("pi_test" + compteur.get(), "secret",
                    "requires_payment_method", appel.getArgument(0), appel.getArgument(1), appel.getArgument(3));
            creees.put(intention.id(), intention);
            return intention;
        });
        when(paiementService.recupererIntention(any())).thenAnswer(appel -> {
            IntentionStripeModel i = creees.get(appel.<String>getArgument(0));
            return new IntentionStripeModel(i.id(), i.clientSecret(), "succeeded", i.montantCentimes(), i.devise(),
                    i.metadonnees());
        });
    }

    private ResultActions webhook(String reference, String statut, int montant) throws Exception {
        byte[] corps = json(Map.of("event", "payment-updated", "data", Map.of("id", "pay_" + reference,
                "transactionId", reference, "amount", montant, "status", statut)))
                .getBytes(StandardCharsets.UTF_8);
        return mockMvc.perform(post("/api/webhooks/moosyl").contentType(MediaType.APPLICATION_JSON).content(corps)
                .header("x-webhook-delivery-id", "livraison-" + UUID.randomUUID())
                .header(SignatureWebhookMoosyl.EN_TETE, SignatureWebhookMoosyl.signer(corps, SECRET_MOOSYL)));
    }

    private ResultActions inscrire(Map<String, Object> corps) throws Exception {
        return mockMvc.perform(post("/api/auth/inscription").contentType(MediaType.APPLICATION_JSON).content(json(corps)));
    }

    private long sondageComplet(String jeton, String titre) throws Exception {
        long id = lire(postAvec(jeton, "/api/sondages", Map.of("titre", titre)).andExpect(status().isCreated()))
                .get("id").asLong();
        long sectionId = lire(postAvec(jeton, "/api/sondages/" + id + "/sections", Map.of("titre", "Section")))
                .at("/sections/0/id").asLong();
        postAvec(jeton, "/api/sondages/" + id + "/sections/" + sectionId + "/questions",
                SondageIntegrationTest.question("Région", "CHOIX_UNIQUE", null, "Kaolack", "Fatick"))
                .andExpect(status().isCreated());
        postAvec(jeton, "/api/sondages/" + id + "/sections/" + sectionId + "/questions",
                SondageIntegrationTest.question("Satisfaction", "ECHELLE", Map.of("max", 10)))
                .andExpect(status().isCreated());
        return id;
    }

    private String jetonPublic(String jeton, long id) throws Exception {
        return lire(getAvec(jeton, "/api/sondages/" + id + "/lien").andExpect(status().isOk())).get("jeton").asText();
    }

    /** Achat simulé directement en base (le parcours de paiement est testé à part). */
    private void crediter(String courriel, int quantite) {
        jdbcTemplate.update("insert into credit_sondage (entreprise_id, solde, total_achete, total_utilise, "
                + "date_modification) values (?, ?, ?, 0, now())", entrepriseId(courriel), quantite, quantite);
    }

    private int solde(String courriel) {
        List<Integer> soldes = jdbcTemplate.queryForList("select solde from credit_sondage where entreprise_id = ?",
                Integer.class, entrepriseId(courriel));
        return soldes.isEmpty() ? 0 : soldes.get(0);
    }

    private Long entrepriseId(String courriel) {
        return utilisateurRepository.findByCourriel(courriel).orElseThrow().getEntreprise().getId();
    }

    private Long abonnementId(String courriel) {
        return jdbcTemplate.queryForObject("select id from abonnement where entreprise_id = ?", Long.class,
                entrepriseId(courriel));
    }

    private static JsonNode ligne(JsonNode revenus, String type, String devise) {
        for (JsonNode ligne : revenus.get("lignes")) {
            if (ligne.get("type").asText().equals(type) && ligne.get("devise").asText().equals(devise)) {
                return ligne;
            }
        }
        throw new AssertionError("Aucune ligne " + type + " " + devise);
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
