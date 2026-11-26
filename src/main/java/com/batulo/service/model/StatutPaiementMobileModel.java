package com.batulo.service.model;

import com.batulo.enums.TypePaiement;
import com.batulo.enums.Formule;
import com.batulo.enums.StatutPaiement;

import java.math.BigDecimal;
import java.time.Instant;

/** Statut d'un paiement mobile et abonnement de l'entreprise après ce paiement. */
public record StatutPaiementMobileModel(
        String reference,
        StatutPaiement statut,
        Formule formule,
        BigDecimal montant,
        String devise,
        Instant dateCreation,
        Instant datePaiement,
        AbonnementModel abonnement,
        TypePaiement type,
        Integer quantite,
        /** Sondages achetés de l'entreprise après ce paiement. */
        CreditsModel credits
) {
}
