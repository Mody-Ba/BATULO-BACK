package com.batulo.service.model;

import com.batulo.enums.StatutPaiement;
import io.swagger.v3.oas.annotations.media.Schema;

/** Résultat d'un webhook Moosyl simulé (profil dev). */
public record SimulationWebhookMoosylResponse(
        String reference,
        String evenement,
        String statutEnvoye,
        @Schema(description = "Réponse HTTP du backend au webhook (200 attendu)")
        int statutHttp,
        String reponse,
        @Schema(description = "Statut du paiement après traitement")
        StatutPaiement statutPaiement
) {
}
