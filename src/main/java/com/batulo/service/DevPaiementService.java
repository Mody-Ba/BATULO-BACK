package com.batulo.service;

import com.batulo.service.model.ConfirmationPaiementModel;
import com.batulo.service.model.IntentionStripeModel;
import com.batulo.service.model.SimulationWebhookMoosylModel;

import java.math.BigDecimal;

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

    /**
     * Simule Moosyl sans tunnel : construit un webhook payment-updated pour un paiement mobile de l'entreprise, le
     * signe avec MOOSYL_WEBHOOK_SECRET et l'envoie en HTTP au backend local (POST /api/webhooks/moosyl), qui le
     * traite comme un vrai webhook (signature, idempotence, activation).
     *
     * @param statut  statut Moosyl (completed, failed, cancelled, pending) ; null : completed
     * @param montant montant annoncé ; null : celui du paiement
     * @throws com.batulo.exception.ResourceNotFoundException paiement mobile inconnu pour cette entreprise
     */
    SimulationWebhookMoosylModel simulerWebhookMoosyl(Long entrepriseId, String reference, String statut,
                                                      BigDecimal montant);
}
