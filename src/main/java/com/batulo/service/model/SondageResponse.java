package com.batulo.service.model;

import com.batulo.enums.StatutSondage;

import java.time.Instant;
import java.util.List;

/** Sondage complet : sections > questions > options, triées par ordre. */
public record SondageResponse(
        Long id,
        Long entrepriseId,
        String titre,
        String description,
        /** Calculé à partir des dates : NON_COMMENCE, EN_COURS ou TERMINE. */
        StatutSondage statut,
        /** false : brouillon (seul état modifiable) ; true : publié (contenu gelé). */
        boolean publie,
        Instant datePublication,
        Instant dateFermeture,
        int version,
        /** Sondage dont celui-ci est issu (source réutilisée ou dupliquée, version précédente) ; null si créé vide. */
        Long sondageOrigineId,
        boolean estModele,
        AuteurResponse creePar,
        Instant dateCreation,
        Instant dateModification,
        List<SectionResponse> sections
) {
}
