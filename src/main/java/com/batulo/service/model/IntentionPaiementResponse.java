package com.batulo.service.model;

import com.batulo.enums.Formule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record IntentionPaiementResponse(
        @Schema(description = "Identifiant à renvoyer à /paiement/confirmer une fois la carte validée", example = "pi_3Q...")
        String paymentIntentId,
        @Schema(description = "À passer à Stripe.js (stripe.confirmCardPayment) côté frontend ; ne jamais le journaliser")
        String clientSecret,
        @Schema(description = "Clé publique Stripe (pk_test_...) pour initialiser Stripe.js")
        String clePublique,
        Formule formule,
        BigDecimal montant,
        String devise
) {
    @Override
    public String toString() {
        return "IntentionPaiementResponse[paymentIntentId=" + paymentIntentId + ", clientSecret=****, formule="
                + formule + ", montant=" + montant + ", devise=" + devise + "]";
    }
}
