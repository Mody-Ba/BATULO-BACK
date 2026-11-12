package com.batulo.service.model;

import com.batulo.enums.StatutSondage;

import java.time.Instant;
import java.util.List;

/** Ligne de la liste des sondages. */
public record SondageResumeModel(
        Long id,
        String titre,
        String description,
        StatutSondage statut,
        boolean publie,
        Instant datePublication,
        Instant dateFermeture,
        int version,
        boolean estModele,
        AuteurModel creePar,
        long nombreSections,
        long nombreQuestions,
        /* Réponses reçues par cette version. */
        long nombreReponses,
        /* Réponses reçues par toutes les versions de la lignée. */
        long nombreReponsesLignee,
        /* Assignés de la lignée ; null pour un gestionnaire (réservé à l'admin). */
        List<GestionnaireAssigneModel> gestionnairesAssignes,
        /* true si la lignée a un brouillon plus récent que cette version (nouvelle version en préparation). */
        boolean brouillonEnPreparation,
        /* Id de ce brouillon, sinon null. */
        Long brouillonEnPreparationId,
        Instant dateCreation,
        Instant dateModification
) {
}
