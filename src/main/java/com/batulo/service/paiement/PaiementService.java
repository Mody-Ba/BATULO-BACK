package com.batulo.service.paiement;

import com.batulo.service.model.IntentionStripeModel;

import java.util.Map;

/**
 * Accès au prestataire de paiement (Stripe, mode TEST pendant le développement).
 * La carte est saisie côté frontend avec Stripe.js : aucune donnée de carte ne transite par le backend.
 */
public interface PaiementService {

    /**
     * Crée un PaymentIntent du montant donné.
     *
     * @param metadonnees recopiées chez Stripe (entrepriseId, abonnementId, formule) et revérifiées à la confirmation
     * @throws com.batulo.exception.StripeNonConfigureException clé secrète absente ou pas en mode test
     * @throws com.batulo.exception.StripeIndisponibleException Stripe refuse ou ne répond pas
     */
    IntentionStripeModel creerIntention(long montantCentimes, String devise, String description,
                                        Map<String, String> metadonnees);

    /** Relit un PaymentIntent chez Stripe : la seule source fiable pour savoir s'il a été payé. */
    IntentionStripeModel recupererIntention(String paymentIntentId);

    /**
     * Paie un PaymentIntent avec la carte de test Stripe pm_card_visa.
     * Réservé à la route de développement (profil dev), pour tester sans frontend.
     */
    IntentionStripeModel payerAvecCarteTest(String paymentIntentId);

    /** Clé publique (pk_test_...) que le frontend passe à Stripe.js. */
    String clePublique();
}
