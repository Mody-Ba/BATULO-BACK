package com.batulo.service.model;

import com.batulo.enums.TypePaiement;
import com.batulo.enums.Formule;

import java.math.BigDecimal;

/** Intention de paiement créée chez Stripe : de quoi afficher le formulaire de carte côté frontend. */
public record IntentionPaiementModel(
        String paymentIntentId,
        String clientSecret,
        String clePublique,
        Formule formule,
        BigDecimal montant,
        String devise,
        TypePaiement type,
        /** Nombre de sondages achetés ; null pour un abonnement. */
        Integer quantite
) {
    /** Le clientSecret permet de payer cette intention : il ne doit pas finir dans un log. */
    @Override
    public String toString() {
        return "IntentionPaiementModel[paymentIntentId=" + paymentIntentId + ", clientSecret=****, formule="
                + formule + ", montant=" + montant + ", devise=" + devise + ", type=" + type + ", quantite="
                + quantite + "]";
    }
}
