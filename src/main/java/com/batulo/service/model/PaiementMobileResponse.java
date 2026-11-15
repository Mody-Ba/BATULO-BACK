package com.batulo.service.model;

import com.batulo.enums.Formule;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

public record PaiementMobileResponse(
        @Schema(description = "Référence Batulo du paiement ; Moosyl la renvoie dans l'URL de retour (?reference=)")
        String reference,
        @Schema(description = "Page de paiement Moosyl où rediriger l'admin")
        String checkoutUrl,
        Formule formule,
        @Schema(example = "40.00")
        BigDecimal montant,
        @Schema(example = "MRU")
        String devise,
        @Schema(description = "Fin de validité de la session de paiement ; null si Moosyl ne la donne pas")
        Instant expireLe
) {
}
