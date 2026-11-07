package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Résultat de la route de développement qui paie un PaymentIntent avec la carte de test Stripe. */
public record CarteTestResponse(
        String paymentIntentId,
        @Schema(description = "Statut Stripe après le paiement", example = "succeeded")
        String statutStripe,
        String message
) {
}
