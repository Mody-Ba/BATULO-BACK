package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Achat de sondages par carte (Stripe). */
public record AchatSondagesRequest(
        @NotNull(message = "la quantité est obligatoire (1 à 1000)")
        @Schema(description = "Nombre de sondages, de 1 à 1000 (400 QUANTITE_INVALIDE sinon)", example = "5",
                minimum = "1", maximum = "1000")
        Integer quantite
) {
}
