package com.batulo.service.model;

/** PaymentIntent à revérifier auprès de Stripe. */
public record ConfirmationPaiementModel(
        String paymentIntentId
) {
}
