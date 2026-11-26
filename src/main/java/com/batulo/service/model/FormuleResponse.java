package com.batulo.service.model;

import com.batulo.enums.TypePaiement;
import com.batulo.enums.Formule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record FormuleResponse(
        Formule formule,
        @Schema(example = "Abonnement mensuel")
        String libelle,
        @Schema(example = "1.00")
        BigDecimal prix,
        @Schema(example = "CAD")
        String devise,
        @Schema(example = "1 mois")
        String duree,
        @Schema(description = "Prix en paiement mobile (Moosyl : Bankily, Sedad, Masrivi...)", example = "40")
        BigDecimal prixMobile,
        @Schema(example = "MRU")
        String deviseMobile,
        @Schema(description = "ABONNEMENT (MENSUEL, ANNUEL : illimité pendant la période) ou ACHAT_SONDAGES (formule "
                + "null ; prix et prixMobile sont les prix d'UN sondage, sans date limite)")
        TypePaiement type,
        @Schema(description = "Achat de sondages : quantité minimale par achat ; null pour un abonnement", example = "1")
        Integer quantiteMin,
        @Schema(description = "Achat de sondages : quantité maximale par achat ; null pour un abonnement",
                example = "1000")
        Integer quantiteMax
) {
}
