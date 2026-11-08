package com.batulo.service.model;

import java.time.Instant;
import java.util.List;

/** Sondage complet : sections > questions > options, triées par ordre. */
public record SondageResponse(
        Long id,
        Long entrepriseId,
        String titre,
        String description,
        AuteurResponse creePar,
        Instant dateCreation,
        Instant dateModification,
        List<SectionResponse> sections
) {
}
