package com.batulo.service.model;

import com.batulo.enums.StatutSondage;

import java.time.Instant;

/** Formulaire réutilisable : un sondage de l'entreprise qui peut servir de base à un nouveau sondage. */
public record FormulaireModel(
        Long id,
        String titre,
        boolean estModele,
        StatutSondage statut,
        int version,
        long nombreQuestions,
        Instant dateCreation,
        Instant dateModification
) {
}
