package com.batulo.service;

import com.batulo.service.model.ConfirmationPaiementModel;
import com.batulo.service.model.IntentionStripeModel;

/**
 * Outils de développement (profil dev uniquement) pour tester le paiement sans frontend.
 * N'active rien chez Batulo : après ce paiement de test, il faut toujours appeler /paiement/confirmer.
 */
public interface DevPaiementService {

    /**
     * Paie, côté Stripe, un PaymentIntent de l'entreprise avec la carte de test pm_card_visa.
     *
     * @throws com.batulo.exception.ResourceNotFoundException paiement inconnu pour cette entreprise
     */
    IntentionStripeModel payerAvecCarteTest(Long entrepriseId, ConfirmationPaiementModel paiement);
}
