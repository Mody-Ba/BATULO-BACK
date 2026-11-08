package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Dates d'ouverture et de fermeture (publication ou ajustement).
 * datePublication absente : maintenant (publication) ou inchangée (ajustement).
 * dateFermeture absente : pas de fermeture prévue.
 */
public record DatesSondageRequest(
        @Schema(description = "Ouverture ; absente = maintenant à la publication, inchangée en ajustement",
                example = "2026-11-15T08:00:00Z")
        Instant datePublication,

        @Schema(description = "Fermeture prévue ; absente = pas de fermeture prévue (fermeture manuelle)",
                example = "2026-12-15T23:59:00Z")
        Instant dateFermeture
) {
}
