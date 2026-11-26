package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Sondages achetés à l'unité (crédits sans date limite)")
public record CreditsResponse(
        @Schema(description = "Sondages encore disponibles : un est utilisé à la publication d'un nouveau sondage hors "
                + "abonnement actif", example = "4")
        int solde,
        @Schema(description = "Total acheté depuis l'inscription", example = "5")
        int totalAchete,
        @Schema(description = "Total utilisé (lignées publiées avec un crédit)", example = "1")
        int totalUtilise,
        @Schema(description = "Vrai si l'entreprise a déjà acheté des sondages : elle utilise alors Batulo sans "
                + "abonnement actif (seule la publication d'un nouveau sondage exige un crédit)")
        boolean compteCredits
) {
}
