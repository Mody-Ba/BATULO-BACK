package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import com.batulo.enums.TypePaiement;
public record PaiementConfirmeResponse(
        String message,
        AbonnementResponse abonnement,
        @Schema(description = "ABONNEMENT ou ACHAT_SONDAGES")
        TypePaiement type,
        @Schema(description = "Nombre de sondages achetés ; null pour un abonnement")
        Integer quantite,
        @Schema(description = "Solde de sondages de l'entreprise après ce paiement")
        CreditsResponse credits
) {
}
