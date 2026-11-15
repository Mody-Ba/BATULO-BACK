package com.batulo.service;

import com.batulo.entity.Paiement;
import com.batulo.enums.StatutPaiement;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.PaiementRepository;
import com.batulo.service.model.ConfirmationPaiementModel;
import com.batulo.service.model.IntentionStripeModel;
import com.batulo.service.model.SimulationWebhookMoosylModel;
import com.batulo.service.paiement.PaiementService;
import com.batulo.service.paiement.SignatureWebhookMoosyl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** N'existe qu'avec le profil dev : en production, ni ce service ni sa route ne sont créés. */
@Service
@Profile("dev")
@Transactional(readOnly = true)
public class DevPaiementServiceImpl implements DevPaiementService {

    private static final Logger log = LoggerFactory.getLogger(DevPaiementServiceImpl.class);

    private final PaiementRepository paiementRepository;
    private final PaiementService paiementService;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @Value("${batulo.moosyl.secret-webhook}")
    private String secretWebhook;

    public DevPaiementServiceImpl(PaiementRepository paiementRepository, PaiementService paiementService,
                                  ObjectMapper objectMapper, Environment environment) {
        this.paiementRepository = paiementRepository;
        this.paiementService = paiementService;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Override
    public IntentionStripeModel payerAvecCarteTest(Long entrepriseId, ConfirmationPaiementModel paiement) {
        String paymentIntentId = paiement.paymentIntentId();
        boolean appartientAEntreprise = paiementRepository.findByStripePaymentIntentId(paymentIntentId)
                .map(p -> p.getAbonnement().getEntreprise().getId().equals(entrepriseId))
                .orElse(false);
        if (!appartientAEntreprise) {
            throw new ResourceNotFoundException("PAIEMENT_INTROUVABLE",
                    "Aucun paiement de votre entreprise ne correspond à ce paymentIntentId.");
        }
        IntentionStripeModel intention = paiementService.payerAvecCarteTest(paymentIntentId);
        log.info("[DEV] PaymentIntent {} payé avec la carte de test : statut Stripe {}", paymentIntentId, intention.statut());
        return intention;
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SimulationWebhookMoosylModel simulerWebhookMoosyl(Long entrepriseId, String reference, String statut,
                                                             BigDecimal montant) {
        Paiement paiement = paiementRepository.trouverMoosylDeLEntreprise(reference, entrepriseId)
                .orElseThrow(() -> new ResourceNotFoundException("PAIEMENT_INTROUVABLE",
                        "Aucun paiement mobile de votre entreprise ne correspond à cette référence."));
        String statutEnvoye = statut == null ? "completed" : statut;
        String idPaiement = "dev-" + UUID.randomUUID();
        Instant maintenant = Instant.now();

        // Même forme qu'un webhook Moosyl (https://docs.moosyl.com/webhooks), montant en MRU entiers.
        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", idPaiement);
        data.put("transactionId", paiement.getReference());
        data.put("amount", montant != null ? montant : paiement.getMontant().stripTrailingZeros());
        data.put("status", statutEnvoye);
        data.put("paymentRequestId", "dev-request");
        data.putNull("metadata");
        data.put("completedAt", "completed".equals(statutEnvoye) ? maintenant.toString() : null);
        data.put("createdAt", maintenant.toString());
        data.put("updatedAt", maintenant.toString());
        ObjectNode evenement = objectMapper.createObjectNode();
        evenement.put("event", "payment-updated");
        evenement.set("data", data);
        byte[] corps;
        try {
            corps = objectMapper.writeValueAsBytes(evenement);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }

        // Envoyé au backend lui-même, en HTTP, comme le ferait Moosyl : la route publique du webhook est exercée.
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        ResponseEntity<String> reponse = RestClient.create().post()
                .uri("http://localhost:" + port + "/api/webhooks/moosyl")
                .contentType(MediaType.APPLICATION_JSON)
                .header(SignatureWebhookMoosyl.EN_TETE, SignatureWebhookMoosyl.signer(corps, secretWebhook))
                .header("x-webhook-event", "payment-updated")
                .header("x-webhook-delivery-id", "dev-" + UUID.randomUUID())
                .body(corps)
                .retrieve()
                .onStatus(code -> true, (requete, r) -> { })
                .toEntity(String.class);

        // Requête scalaire : le paiement chargé plus haut est encore dans la session, avec son ancien statut.
        StatutPaiement apres = paiementRepository.statutParReference(reference).orElse(null);
        log.info("[DEV] Webhook Moosyl simulé pour {} ({}) : HTTP {}, paiement {}", reference, statutEnvoye,
                reponse.getStatusCode().value(), apres);
        return new SimulationWebhookMoosylModel(reference, "payment-updated", statutEnvoye,
                reponse.getStatusCode().value(), reponse.getBody(), apres);
    }
}
