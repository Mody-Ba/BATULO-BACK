package com.batulo.service.model;

import com.batulo.enums.RelationSondage;
import com.batulo.enums.StatutSondage;

import java.time.Instant;

/** Un sondage visible par un gestionnaire, et pourquoi (créateur ou assigné). */
public record SondageGestionnaireResponse(
        Long id,
        String titre,
        StatutSondage statut,
        boolean publie,
        int version,
        RelationSondage relation,
        Instant dateModification
) {
}
