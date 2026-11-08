package com.batulo.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Réglages d'analyse d'une question, modifiables à tout moment (même après publication). */
public record CritereRequest(
        @NotNull(message = "estCritere est obligatoire (true ou false)")
        @Schema(example = "true")
        Boolean estCritere,

        @Schema(description = "NUMERIQUE : {\"tranches\": [18, 30, 45, 60]} (absent : 5 tranches égales entre min et "
                + "max) ; DATE : {\"granularite\": \"MOIS\"} ou \"ANNEE\" ; vide pour CHOIX_UNIQUE et OUI_NON",
                example = "{\"tranches\": [18, 30, 45, 60]}")
        JsonNode critereConfig
) {
}
