package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** Webhook Moosyl à simuler (profil dev) pour un paiement mobile de l'entreprise. */
public record SimulationWebhookMoosylRequest(
        @NotBlank(message = "la référence du paiement est obligatoire")
        String reference,

        @Pattern(regexp = "completed|failed|cancelled|pending", message = "statut : completed, failed, cancelled ou pending")
        @Schema(description = "Statut Moosyl envoyé ; completed par défaut", example = "completed")
        String statut,

        @Schema(description = "Montant annoncé ; par défaut celui du paiement (un autre montant teste le refus)")
        BigDecimal montant
) {
}
