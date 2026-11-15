package com.batulo.integration;

import com.batulo.entity.Utilisateur;
import com.batulo.exception.MoosylIndisponibleException;
import com.batulo.exception.MoosylNonConfigureException;
import com.batulo.service.model.SessionMoosylModel;
import com.batulo.service.paiement.MoosylClient;
import com.batulo.service.paiement.SignatureWebhookMoosyl;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Paiement mobile Moosyl : création de la session, webhook signé (source principale), relecture du statut, et
 * une seule activation par paiement quels que soient les réessais et les relectures.
 */
class PaiementMobileIntegrationTest extends PostgresIntegrationTestBase {

    /** Secret du profil de test (application-test.yml). */
    private static final String SECRET = "secret-webhook-moosyl-de-test";
    private static final String URL_RETOUR = "http://localhost:4200/admin/abonnement/paiement-mobile";

    @MockitoBean
    MoosylClient moosylClient;

    private Utilisateur admin;
    private String jetonAdmin;

    @BeforeEach
    void preparer() throws Exception {
        superAdmin("sa@test.com");
        admin = admin("awa@test.com", null);
        jetonAdmin = jeton("awa@test.com");
        when(moosylClient.creerSession(anyString(), anyLong(), any(), anyString(), anyInt())).thenAnswer(appel ->
                new SessionMoosylModel("cs_" + appel.getArgument(0), "https://payments.moosyl.com/checkout/cs_"
                        + appel.getArgument(0), Instant.now().plus(Duration.ofMinutes(30))));
        when(moosylClient.statutSession(anyString())).thenReturn(Optional.of("open"));
    }

    // ------------------------------------------------------------------ Démarrer un paiement

    @Test
    void demarrerCreeLePaiementEtLaSession() throws Exception {
        JsonNode paiement = lire(postAvec(jetonAdmin, "/api/abonnements/paiement-mobile",
                Map.of("formule", "MENSUEL", "telephone", "22222222"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.formule").value("MENSUEL"))
                .andExpect(jsonPath("$.montant").value(40.00))
                .andExpect(jsonPath("$.devise").value("MRU"))
                .andExpect(jsonPath("$.expireLe").isNotEmpty()));
        String reference = paiement.get("reference").asText();
        assertThat(paiement.get("checkoutUrl").asText()).isEqualTo("https://payments.moosyl.com/checkout/cs_" + reference);
        verify(moosylClient).creerSession(reference, 40L, "22222222", URL_RETOUR + "?reference=" + reference, 30);

        assertThat(jdbcTemplate.queryForMap("select fournisseur, statut, devise, montant, session_externe_id, "
                + "stripe_payment_intent_id from paiement where reference = ?", reference))
                .containsEntry("fournisseur", "MOOSYL").containsEntry("statut", "EN_ATTENTE")
                .containsEntry("devise", "MRU").containsEntry("session_externe_id", "cs_" + reference)
                .containsEntry("stripe_payment_intent_id", null);
        assertThat(((Number) jdbcTemplate.queryForObject("select montant from paiement where reference = ?",
                Object.class, reference)).intValue()).isEqualTo(40);

        // Annuel, sans téléphone (Moosyl le demandera).
        JsonNode annuel = lire(postAvec(jetonAdmin, "/api/abonnements/paiement-mobile", Map.of("formule", "ANNUEL"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.montant").value(80.00)));
        verify(moosylClient).creerSession(eq(annuel.get("reference").asText()), eq(80L), eq(null),
                startsWith(URL_RETOUR + "?reference="), eq(30));
        // L'abonnement n'est pas modifié tant que Moosyl n'a pas confirmé.
        getAvec(jetonAdmin, "/api/abonnements/moi").andExpect(jsonPath("$.statut").value("EN_ATTENTE_PAIEMENT"));
    }

    @Test
    void demarrerValidationEtErreursDeMoosyl() throws Exception {
        postAvec(jetonAdmin, "/api/abonnements/paiement-mobile", Map.of("formule", "MENSUEL", "telephone", "1234"))
                .andExpect(status().isBadRequest());
        postAvec(jetonAdmin, "/api/abonnements/paiement-mobile", Map.of()).andExpect(status().isBadRequest());

        when(moosylClient.creerSession(anyString(), anyLong(), any(), anyString(), anyInt()))
                .thenThrow(new MoosylIndisponibleException("Moosyl a refusé le paiement (HTTP 401) : Invalid API key."));
        postAvec(jetonAdmin, "/api/abonnements/paiement-mobile", Map.of("formule", "MENSUEL"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("MOOSYL_ERREUR"));
        // Échec chez Moosyl : aucun paiement enregistré.
        assertThat(jdbcTemplate.queryForObject("select count(*) from paiement", Long.class)).isZero();

        doThrow(new MoosylNonConfigureException("non configuré"))
                .when(moosylClient).creerSession(anyString(), anyLong(), any(), anyString(), anyInt());
        postAvec(jetonAdmin, "/api/abonnements/paiement-mobile", Map.of("formule", "MENSUEL"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("PAIEMENT_MOBILE_NON_CONFIGURE"));
        assertThat(jdbcTemplate.queryForObject("select count(*) from paiement", Long.class)).isZero();
    }

    // ------------------------------------------------------------------ Webhook

    @Test
    void webhookPayeActiveUneSeuleFois() throws Exception {
        String reference = demarrer("MENSUEL");
        Instant avant = Instant.now();

        webhook("payment-created", reference, "completed", 40).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultat").value("TRAITE"));
        Instant expiration = expiration();
        assertThat(expiration).isBetween(avant.plus(Duration.ofDays(27)), avant.plus(Duration.ofDays(32)));
        assertThat(statutPaiement(reference)).isEqualTo("REUSSI");

        // Moosyl réessaie (même livraison), puis envoie payment-updated : rien ne change.
        webhook("payment-created", reference, "completed", 40).andExpect(status().isOk());
        webhook("payment-updated", reference, "completed", 40).andExpect(status().isOk());
        assertThat(expiration()).isEqualTo(expiration);
        assertThat(notifications("PAIEMENT_RECU")).isEqualTo(1);

        // Le statut ne relit plus Moosyl : le paiement n'est plus en attente.
        getAvec(jetonAdmin, "/api/abonnements/paiement-mobile/" + reference)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("REUSSI"))
                .andExpect(jsonPath("$.datePaiement").isNotEmpty())
                .andExpect(jsonPath("$.abonnement.statut").value("ACTIF"))
                .andExpect(jsonPath("$.abonnement.formule").value("MENSUEL"))
                .andExpect(jsonPath("$.abonnement.devise").value("MRU"));
        verify(moosylClient, never()).statutSession(anyString());

        // Historique des paiements : fournisseur MOOSYL.
        getAvec(jetonAdmin, "/api/abonnements/paiements")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.contenu[0].fournisseur").value("MOOSYL"))
                .andExpect(jsonPath("$.contenu[0].devise").value("MRU"));
    }

    @Test
    void renouvellementPayeProlongeDepuisLExpiration() throws Exception {
        Instant finActuelle = Instant.now().plus(Duration.ofDays(10)).truncatedTo(ChronoUnit.SECONDS);
        jdbcTemplate.update("update abonnement set date_expiration = ?, formule = 'MENSUEL', statut = 'ACTIF' "
                + "where entreprise_id = ?", Timestamp.from(finActuelle), admin.getEntreprise().getId());
        String reference = demarrer("ANNUEL");
        webhook("payment-updated", reference, "completed", 80).andExpect(status().isOk());
        assertThat(expiration()).isEqualTo(finActuelle.atZone(java.time.ZoneOffset.UTC).plusYears(1).toInstant());
    }

    @Test
    void webhookSignatureEtCorps() throws Exception {
        String reference = demarrer("MENSUEL");
        byte[] corps = corps("payment-updated", reference, "completed", 40);

        envoyer(corps, null).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SIGNATURE_INVALIDE"));
        envoyer(corps, SignatureWebhookMoosyl.signer(corps, "autre-secret")).andExpect(status().isUnauthorized());
        envoyer(corps, "sha256=zz").andExpect(status().isUnauthorized());
        // Corps modifié après signature.
        byte[] modifie = corps("payment-updated", reference, "completed", 1);
        envoyer(modifie, SignatureWebhookMoosyl.signer(corps, SECRET)).andExpect(status().isUnauthorized());
        assertThat(statutPaiement(reference)).isEqualTo("EN_ATTENTE");

        // Signature sans le préfixe sha256= : acceptée (comme le SDK officiel).
        envoyer(corps, SignatureWebhookMoosyl.signer(corps, SECRET).substring("sha256=".length()))
                .andExpect(status().isOk());
        assertThat(statutPaiement(reference)).isEqualTo("REUSSI");

        byte[] illisible = "pas du json".getBytes(StandardCharsets.UTF_8);
        envoyer(illisible, SignatureWebhookMoosyl.signer(illisible, SECRET))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEBHOOK_INVALIDE"));
    }

    @Test
    void webhookSansEffet() throws Exception {
        String reference = demarrer("MENSUEL");
        // Montant différent : abonnement inchangé, 200 pour que Moosyl ne réessaie pas.
        webhook("payment-updated", reference, "completed", 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultat").value("IGNORE"));
        assertThat(statutPaiement(reference)).isEqualTo("EN_ATTENTE");
        // Référence inconnue, paiement en cours, autre événement.
        webhook("payment-updated", "inconnue", "completed", 40).andExpect(jsonPath("$.resultat").value("IGNORE"));
        webhook("payment-updated", reference, "pending", 40).andExpect(jsonPath("$.resultat").value("IGNORE"));
        webhook("payment-request-created", reference, "completed", 40)
                .andExpect(jsonPath("$.resultat").value("IGNORE"));
        assertThat(statutPaiement(reference)).isEqualTo("EN_ATTENTE");
        getAvec(jetonAdmin, "/api/abonnements/moi").andExpect(jsonPath("$.statut").value("EN_ATTENTE_PAIEMENT"));
    }

    @Test
    void echecPuisNouvelEssaiReussi() throws Exception {
        String reference = demarrer("MENSUEL");
        webhook("payment-created", reference, "failed", 40).andExpect(status().isOk());
        assertThat(statutPaiement(reference)).isEqualTo("ECHOUE");
        getAvec(jetonAdmin, "/api/abonnements/paiement-mobile/" + reference)
                .andExpect(jsonPath("$.statut").value("ECHOUE"));

        // Nouvel essai dans la même session : payé.
        webhook("payment-created", reference, "completed", 40).andExpect(status().isOk());
        assertThat(statutPaiement(reference)).isEqualTo("REUSSI");
        // Un échec tardif ne défait pas un paiement réussi.
        webhook("payment-updated", reference, "cancelled", 40).andExpect(status().isOk());
        assertThat(statutPaiement(reference)).isEqualTo("REUSSI");
    }

    // ------------------------------------------------------------------ Relecture du statut

    @Test
    void relectureDuStatutActiveSansWebhookPuisLeWebhookNeRecomptePas() throws Exception {
        String reference = demarrer("MENSUEL");
        getAvec(jetonAdmin, "/api/abonnements/paiement-mobile/" + reference)
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.datePaiement").doesNotExist());
        verify(moosylClient).statutSession("cs_" + reference);

        // Moosyl ne répond pas : le paiement reste en attente.
        when(moosylClient.statutSession(anyString())).thenReturn(Optional.empty());
        getAvec(jetonAdmin, "/api/abonnements/paiement-mobile/" + reference)
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"));

        // Session payée : activation à la lecture du statut.
        when(moosylClient.statutSession(anyString())).thenReturn(Optional.of("completed"));
        getAvec(jetonAdmin, "/api/abonnements/paiement-mobile/" + reference)
                .andExpect(jsonPath("$.statut").value("REUSSI"))
                .andExpect(jsonPath("$.abonnement.statut").value("ACTIF"));
        Instant expiration = expiration();

        // Le webhook arrive ensuite, et une autre lecture : toujours une seule prolongation.
        webhook("payment-created", reference, "completed", 40).andExpect(status().isOk());
        getAvec(jetonAdmin, "/api/abonnements/paiement-mobile/" + reference).andExpect(status().isOk());
        assertThat(expiration()).isEqualTo(expiration);
        assertThat(notifications("PAIEMENT_RECU")).isEqualTo(1);
    }

    @Test
    void sessionExpireeMarqueLEchec() throws Exception {
        String reference = demarrer("MENSUEL");
        when(moosylClient.statutSession(anyString())).thenReturn(Optional.of("expired"));
        getAvec(jetonAdmin, "/api/abonnements/paiement-mobile/" + reference)
                .andExpect(jsonPath("$.statut").value("ECHOUE"));
        getAvec(jetonAdmin, "/api/abonnements/moi").andExpect(jsonPath("$.statut").value("EN_ATTENTE_PAIEMENT"));
    }

    @Test
    void webhooksSimultanesNeProlongentQuUneFois() throws Exception {
        String reference = demarrer("MENSUEL");
        byte[] corps = corps("payment-updated", reference, "completed", 40);
        String signature = SignatureWebhookMoosyl.signer(corps, SECRET);
        when(moosylClient.statutSession(anyString())).thenReturn(Optional.of("completed"));

        CountDownLatch depart = new CountDownLatch(1);
        ExecutorService executeur = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> resultats = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                boolean parWebhook = i % 2 == 0;
                Callable<Integer> tache = () -> {
                    depart.await();
                    return (parWebhook ? envoyer(corps, signature)
                            : getAvec(jetonAdmin, "/api/abonnements/paiement-mobile/" + reference))
                            .andReturn().getResponse().getStatus();
                };
                resultats.add(executeur.submit(tache));
            }
            depart.countDown();
            for (Future<Integer> resultat : resultats) {
                assertThat(resultat.get()).isEqualTo(200);
            }
        } finally {
            executeur.shutdownNow();
        }
        assertThat(statutPaiement(reference)).isEqualTo("REUSSI");
        assertThat(notifications("PAIEMENT_RECU")).isEqualTo(1);
        Instant expiration = expiration();
        assertThat(expiration).isBefore(Instant.now().plus(Duration.ofDays(32)));
    }

    // ------------------------------------------------------------------ Droits, isolation, formules

    @Test
    void droitsEtIsolation() throws Exception {
        String reference = demarrer("MENSUEL");
        adminActif("autre@test.com");
        getAvec(jeton("autre@test.com"), "/api/abonnements/paiement-mobile/" + reference)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PAIEMENT_INTROUVABLE"));
        getAvec(jetonAdmin, "/api/abonnements/paiement-mobile/inconnue").andExpect(status().isNotFound());

        Utilisateur actif = adminActif("actif@test.com");
        gestionnaire("g@test.com", actif);
        postAvec(jeton("g@test.com"), "/api/abonnements/paiement-mobile", Map.of("formule", "MENSUEL"))
                .andExpect(status().isForbidden());
        postAvec(jeton("sa@test.com"), "/api/abonnements/paiement-mobile", Map.of("formule", "MENSUEL"))
                .andExpect(status().isForbidden());
        postAvec(null, "/api/abonnements/paiement-mobile", Map.of("formule", "MENSUEL"))
                .andExpect(status().isUnauthorized());

        // Abonnement expiré : le paiement reste possible.
        Utilisateur expire = admin("expire@test.com", Instant.now().minus(Duration.ofDays(2)));
        postAvec(jeton("expire@test.com"), "/api/abonnements/paiement-mobile", Map.of("formule", "MENSUEL"))
                .andExpect(status().isCreated());
        assertThat(expire.getId()).isNotNull();
    }

    @Test
    void formulesAvecLePrixMobile() throws Exception {
        mockMvc.perform(get("/api/abonnements/formules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.formule == 'MENSUEL')].prixMobile").value(40))
                .andExpect(jsonPath("$[?(@.formule == 'ANNUEL')].prixMobile").value(80))
                .andExpect(jsonPath("$[0].deviseMobile").value("MRU"))
                .andExpect(jsonPath("$[0].devise").value("CAD"));
    }

    // ------------------------------------------------------------------ Outils

    private String demarrer(String formule) throws Exception {
        return lire(postAvec(jetonAdmin, "/api/abonnements/paiement-mobile", Map.of("formule", formule))
                .andExpect(status().isCreated())).get("reference").asText();
    }

    private byte[] corps(String evenement, String reference, String statut, int montant) throws Exception {
        return json(Map.of("event", evenement, "data", Map.of("id", "pay_" + reference, "transactionId", reference,
                "amount", montant, "status", statut, "paymentRequestId", "pr_" + reference)))
                .getBytes(StandardCharsets.UTF_8);
    }

    private ResultActions webhook(String evenement, String reference, String statut, int montant) throws Exception {
        byte[] corps = corps(evenement, reference, statut, montant);
        return envoyer(corps, SignatureWebhookMoosyl.signer(corps, SECRET));
    }

    private ResultActions envoyer(byte[] corps, String signature) throws Exception {
        var requete = post("/api/webhooks/moosyl").contentType(MediaType.APPLICATION_JSON).content(corps)
                .header("x-webhook-delivery-id", "livraison-1").header("user-agent", "Moosyl-Webhook/1.0");
        if (signature != null) {
            requete.header(SignatureWebhookMoosyl.EN_TETE, signature);
        }
        return mockMvc.perform(requete);
    }

    private String statutPaiement(String reference) {
        return jdbcTemplate.queryForObject("select statut from paiement where reference = ?", String.class, reference);
    }

    private Instant expiration() {
        Timestamp date = jdbcTemplate.queryForObject("select date_expiration from abonnement where entreprise_id = ?",
                Timestamp.class, admin.getEntreprise().getId());
        return date == null ? null : date.toInstant();
    }

    private long notifications(String type) {
        return jdbcTemplate.queryForObject("select count(*) from notification where type = ?", Long.class, type);
    }

    private JsonNode lire(ResultActions resultat) throws Exception {
        return objectMapper.readTree(resultat.andReturn().getResponse().getContentAsString());
    }
}
