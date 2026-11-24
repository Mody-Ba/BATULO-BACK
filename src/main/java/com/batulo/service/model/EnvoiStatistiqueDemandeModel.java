package com.batulo.service.model;

import com.batulo.enums.GranulariteDate;
import com.batulo.enums.Langue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record EnvoiStatistiqueDemandeModel(
        String commentaire,
        boolean lignee,
        LocalDate du,
        LocalDate au,
        Map<String, List<String>> criteres,
        GranulariteDate granulariteDate,
        /** Filtre sur la langue de réponse (vide : toutes), enregistré avec l'envoi. */
        List<Langue> languesReponse
) {
}
