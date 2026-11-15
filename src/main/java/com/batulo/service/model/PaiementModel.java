package com.batulo.service.model;

import com.batulo.enums.Formule;
import com.batulo.enums.FournisseurPaiement;

import java.math.BigDecimal;
import java.time.Instant;

/** Paiement réussi de l'historique d'une entreprise. */
public record PaiementModel(
        Long id,
        Instant datePaiement,
        Formule formule,
        BigDecimal montant,
        String devise,
        /** STRIPE (carte) ou MOOSYL (paiement mobile). */
        FournisseurPaiement fournisseur
) {
}
