package com.batulo.service.model;

import java.time.Instant;
import java.util.List;

/**
 * Formulaire affiché au répondant (RE-01, RE-02) : uniquement ce qu'il faut pour répondre, aucune donnée interne
 * (ni id de sondage, ni entreprise, ni créateur, ni version).
 */
public record FormulairePublicModel(
        String titre,
        String description,
        /** true : une seule réponse par appareil ; le frontend envoie alors son jetonRepondant. */
        boolean reponseUnique,
        /** Fermeture prévue ; null si aucune. */
        Instant dateFermeture,
        List<SectionModel> sections
) {
}
