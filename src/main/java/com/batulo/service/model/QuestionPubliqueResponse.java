package com.batulo.service.model;

import com.fasterxml.jackson.annotation.JsonInclude;
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
        List<OptionPubliqueResponse> options,
        /**
         * Condition d'affichage, pour masquer et afficher en direct ; null : toujours affichée. Le serveur recalcule
         * la visibilité à l'envoi : une réponse à une question masquée est ignorée.
         */
        ConditionResponse condition,
        /**
         * MATRICE (formulaire public et aperçu) : libellés à afficher, au même rang que configuration.lignes et
         * configuration.colonnes, qui restent les valeurs à envoyer ({"ligne": "colonne"} en langue principale).
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        LibellesMatriceResponse libellesMatrice
) {
}
