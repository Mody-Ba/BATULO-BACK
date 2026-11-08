package com.batulo.service.model;

import com.batulo.enums.StatutSondage;

import java.time.Instant;

/** Ligne de la liste des sondages. */
public record SondageResumeResponse(
        Long id,
        String titre,
        String description,
        StatutSondage statut,
        boolean publie,
        Instant datePublication,
        Instant dateFermeture,
        int version,
        boolean estModele,
        AuteurResponse creePar,
        long nombreSections,
        long nombreQuestions,
        Instant dateCreation,
        Instant dateModification
) {
}
