package com.batulo.service.model;

import com.batulo.enums.Formule;
import com.batulo.enums.FournisseurPaiement;

import java.math.BigDecimal;
import java.time.Instant;

/** Ligne de l'historique des paiements (GET /api/abonnements/paiements). */
public record PaiementResponse(
        Long id,
        Instant datePaiement,
        Formule formule,
        BigDecimal montant,
        String devise,
        /** STRIPE (carte) ou MOOSYL (paiement mobile). */
        FournisseurPaiement fournisseur
) {
}
