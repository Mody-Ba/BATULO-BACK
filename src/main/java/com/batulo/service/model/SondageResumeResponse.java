package com.batulo.service.model;

import java.time.Instant;

/** Ligne de la liste des sondages. */
public record SondageResumeResponse(
        Long id,
        String titre,
        String description,
        AuteurResponse creePar,
        long nombreSections,
        long nombreQuestions,
        Instant dateCreation,
        Instant dateModification
) {
}
