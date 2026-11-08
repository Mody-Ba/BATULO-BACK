package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Marquer ou démarquer un sondage comme modèle de formulaire. */
public record ModeleRequest(
        @NotNull(message = "estModele est obligatoire (true ou false)")
        @Schema(example = "true")
        Boolean estModele
) {
}
