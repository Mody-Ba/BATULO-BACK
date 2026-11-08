package com.batulo.service.model;

import com.batulo.enums.StatutSondage;

import java.time.Instant;

/** Une version d'une lignée de sondages. */
public record VersionModel(
        Long id,
        int version,
        String titre,
        StatutSondage statut,
        boolean publie,
        Instant datePublication,
        Instant dateFermeture,
        Instant dateCreation
) {
}
