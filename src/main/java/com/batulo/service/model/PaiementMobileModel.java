package com.batulo.service.model;

import com.batulo.enums.TypePaiement;
import com.batulo.enums.Formule;

import java.math.BigDecimal;
import java.time.Instant;

/** Paiement mobile créé : la page Moosyl où rediriger l'admin, et la référence pour suivre le statut. */
public record PaiementMobileModel(
        String reference,
        String checkoutUrl,
        Formule formule,
        BigDecimal montant,
        String devise,
        Instant expireLe,
        TypePaiement type,
        Integer quantite
) {
}
