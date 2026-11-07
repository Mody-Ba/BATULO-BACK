package com.batulo.service.model;

import com.batulo.enums.Formule;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Choix de la formule à payer. */
public record IntentionPaiementRequest(
        @NotNull(message = "la formule est obligatoire (MENSUEL ou ANNUEL)")
        @Schema(example = "MENSUEL")
        Formule formule
) {
}
