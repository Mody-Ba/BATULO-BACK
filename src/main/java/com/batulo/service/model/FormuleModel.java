package com.batulo.service.model;

import com.batulo.enums.TypePaiement;
import com.batulo.enums.Formule;

import java.math.BigDecimal;

/** Une formule d'abonnement et son prix (lu dans application.yml). */
public record FormuleModel(
        Formule formule,
        String libelle,
        BigDecimal prix,
        String devise,
        String duree,
        /** Prix en paiement mobile (Moosyl), en MRU entiers. */
        BigDecimal prixMobile,
        String deviseMobile,
        /** ABONNEMENT (formule renseignée) ou ACHAT_SONDAGES (formule null, prix unitaires, quantités). */
        TypePaiement type,
        Integer quantiteMin,
        Integer quantiteMax
) {
}
