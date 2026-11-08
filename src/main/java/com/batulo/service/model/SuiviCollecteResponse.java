package com.batulo.service.model;

import com.batulo.enums.StatutSondage;

import java.time.Instant;

/** Suivi de la collecte d'une version d'un sondage. */
public record SuiviCollecteResponse(
        Long sondageId,
        int version,
        StatutSondage statut,
        /** true si cette version accepte des réponses en ce moment. */
        boolean collecteOuverte,
        /** Pourquoi la collecte est fermée ; null si elle est ouverte. */
        String raisonFermeture,
        /** Réponses à cette version. */
        long nombreReponses,
        /** Réponses à toutes les versions du sondage. */
        long nombreReponsesTotal,
        /** Dernière réponse à cette version ; null s'il n'y en a pas. */
        Instant derniereReponse
) {
}
