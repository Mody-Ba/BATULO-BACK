package com.batulo.service.paiement;

import com.batulo.exception.ResourceNotFoundException;
import com.batulo.exception.StripeIndisponibleException;
import com.batulo.exception.StripeNonConfigureException;
import com.batulo.service.model.IntentionStripeModel;
import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentConfirmParams;
import com.stripe.param.PaymentIntentCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;

/**
 * Implémentation Stripe de {@link PaiementService}, avec l'API PaymentIntent.
 * Seules les clés de TEST sont acceptées pendant le développement. Le client Stripe est créé au premier
 * appel : l'application démarre même si STRIPE_SECRET_KEY n'est pas encore renseignée.
 */
@Service
public class StripePaiementService implements PaiementService {

    private static final Logger log = LoggerFactory.getLogger(StripePaiementService.class);

    /** Carte de test Stripe (Visa qui accepte toujours le paiement). */
    private static final String CARTE_TEST = "pm_card_visa";

    @Value("${batulo.stripe.cle-secrete:}")
    private String cleSecrete;

    @Value("${batulo.stripe.cle-publique:}")
    private String clePublique;

    private volatile StripeClient client;

    @Override
    public IntentionStripeModel creerIntention(long montantCentimes, String devise, String description,
                                               Map<String, String> metadonnees) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(montantCentimes)
                .setCurrency(devise.toLowerCase(Locale.ROOT))
                .setDescription(description)
                .putAllMetadata(metadonnees)
                // Carte saisie dans la page (Stripe.js), sans redirection vers un autre site.
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                        .build())
                .build();
        try {
            return versModele(client().v1().paymentIntents().create(params));
        } catch (StripeException e) {
            log.warn("Création du PaymentIntent refusée par Stripe : {}", e.getMessage());
            throw new StripeIndisponibleException("Stripe a refusé la création du paiement. Réessayez plus tard.");
        }
    }

    @Override
    public IntentionStripeModel recupererIntention(String paymentIntentId) {
        try {
            return versModele(client().v1().paymentIntents().retrieve(paymentIntentId));
        } catch (StripeException e) {
            throw erreurLecture(paymentIntentId, e);
        }
    }

    @Override
    public IntentionStripeModel payerAvecCarteTest(String paymentIntentId) {
        PaymentIntentConfirmParams params = PaymentIntentConfirmParams.builder()
                .setPaymentMethod(CARTE_TEST)
                .build();
        try {
            return versModele(client().v1().paymentIntents().confirm(paymentIntentId, params));
        } catch (StripeException e) {
            if (e instanceof InvalidRequestException && "payment_intent_unexpected_state".equals(e.getCode())) {
                // Déjà payé ou annulé : on renvoie l'état actuel plutôt qu'une erreur.
                return recupererIntention(paymentIntentId);
            }
            log.warn("Paiement de test du PaymentIntent {} refusé par Stripe : {}", paymentIntentId, e.getMessage());
            throw erreurLecture(paymentIntentId, e);
        }
    }

    @Override
    public String clePublique() {
        if (clePublique == null || clePublique.isBlank()) {
            throw new StripeNonConfigureException("STRIPE_NON_CONFIGURE",
                    "STRIPE_PUBLISHABLE_KEY n'est pas renseignée (voir .env.properties).");
        }
        if (!clePublique.startsWith("pk_test_")) {
            throw new StripeNonConfigureException("STRIPE_MODE_TEST_REQUIS",
                    "Seule une clé publique Stripe de test (pk_test_...) est acceptée pendant le développement.");
        }
        return clePublique;
    }

    private StripeClient client() {
        if (cleSecrete == null || cleSecrete.isBlank()) {
            throw new StripeNonConfigureException("STRIPE_NON_CONFIGURE",
                    "STRIPE_SECRET_KEY n'est pas renseignée (voir .env.properties).");
        }
        if (!cleSecrete.startsWith("sk_test_") && !cleSecrete.startsWith("rk_test_")) {
            throw new StripeNonConfigureException("STRIPE_MODE_TEST_REQUIS",
                    "Seule une clé secrète Stripe de test (sk_test_...) est acceptée pendant le développement.");
        }
        StripeClient courant = client;
        if (courant == null) {
            synchronized (this) {
                if (client == null) {
                    client = new StripeClient(cleSecrete);
                }
                courant = client;
            }
        }
        return courant;
    }

    private RuntimeException erreurLecture(String paymentIntentId, StripeException e) {
        if (e instanceof InvalidRequestException && "resource_missing".equals(e.getCode())) {
            return new ResourceNotFoundException("PAIEMENT_INTROUVABLE", "Ce paiement est inconnu de Stripe.");
        }
        log.warn("Lecture du PaymentIntent {} impossible : {}", paymentIntentId, e.getMessage());
        return new StripeIndisponibleException("Impossible de vérifier le paiement auprès de Stripe. Réessayez plus tard.");
    }

    private static IntentionStripeModel versModele(PaymentIntent intention) {
        return new IntentionStripeModel(
                intention.getId(),
                intention.getClientSecret(),
                intention.getStatus(),
                intention.getAmount(),
                intention.getCurrency(),
                intention.getMetadata() == null ? Map.of() : Map.copyOf(intention.getMetadata()));
    }
}
