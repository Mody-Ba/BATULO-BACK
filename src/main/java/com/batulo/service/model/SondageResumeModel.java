package com.batulo.service.model;

import java.time.Instant;

/** Ligne de la liste des sondages. */
public record SondageResumeModel(
        Long id,
        String titre,
        String description,
        AuteurModel creePar,
        long nombreSections,
        long nombreQuestions,
        Instant dateCreation,
        Instant dateModification
) {
}
