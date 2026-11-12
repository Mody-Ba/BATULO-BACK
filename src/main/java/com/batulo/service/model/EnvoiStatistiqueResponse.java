package com.batulo.service.model;

import com.batulo.enums.GranulariteDate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Statistiques envoyées par un gestionnaire à son admin (GE-06), avec les filtres appliqués. */
public record EnvoiStatistiqueResponse(
        Long id,
        Long sondageId,
        String sondageTitre,
        int version,
        boolean lignee,
        LocalDate du,
        LocalDate au,
        GranulariteDate granulariteDate,
        /** Critères appliqués, lisibles : « Région : Kayes, Kaolack ». */
        List<String> criteres,
        /** Null si le compte du gestionnaire a été supprimé ; expediteurNom reste. */
        Long gestionnaireId,
        String expediteurNom,
        String commentaire,
        Instant dateEnvoi,
        /** Première ouverture par l'admin ; null : pas encore consulté. */
        Instant consulteLe
) {
}
