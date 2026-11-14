package com.batulo.service.model;

import com.batulo.enums.TypeQuestion;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Question du formulaire public. Volontairement sans réglage d'analyse (critère, découpage, tranches) ni clé :
 * un nouveau champ interne de QuestionResponse ne peut pas atteindre le répondant par accident.
 */
public record QuestionPubliqueResponse(
        Long id,
        String libelle,
        String aide,
        TypeQuestion type,
        boolean obligatoire,
        int ordre,
        /** Configuration d'affichage propre au type (ECHELLE {min,max}, MATRICE {lignes,colonnes}...). */
        JsonNode configuration,
        List<OptionPubliqueResponse> options
) {
}
