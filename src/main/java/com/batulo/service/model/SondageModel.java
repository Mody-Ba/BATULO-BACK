package com.batulo.service.model;

import java.time.Instant;
import java.util.List;

/** Sondage complet : sections > questions > options, triées par ordre. */
public record SondageModel(
        Long id,
        Long entrepriseId,
        String titre,
        String description,
        AuteurModel creePar,
        Instant dateCreation,
        Instant dateModification,
        List<SectionModel> sections
) {
}
