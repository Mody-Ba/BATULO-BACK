package com.batulo.service.model;

import java.util.Map;

/**
 * Ce que Batulo lit d'un PaymentIntent Stripe. Isole le reste du code de la bibliothèque Stripe.
 * Aucune donnée de carte : seulement l'identifiant, le statut, le montant et les métadonnées.
 */
public record IntentionStripeModel(
        String id,
        String clientSecret,
        /** Statut Stripe : requires_payment_method, processing, succeeded, canceled... */
        String statut,
        Long montantCentimes,
        String devise,
        Map<String, String> metadonnees
) {
    @Override
    public String toString() {
        return "IntentionStripeModel[id=" + id + ", clientSecret=****, statut=" + statut + ", montantCentimes="
                + montantCentimes + ", devise=" + devise + ", metadonnees=" + metadonnees + "]";
    }
}
