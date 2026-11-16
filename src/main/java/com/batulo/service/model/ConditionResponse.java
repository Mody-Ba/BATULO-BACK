package com.batulo.service.model;

import com.batulo.enums.CombinaisonCondition;

import java.util.List;

/**
 * Condition d'affichage d'une question ou d'une section, renvoyée à l'éditeur, à l'aperçu et au formulaire public pour
 * masquer et afficher en direct. Le serveur la recalcule toujours à l'envoi. Aucune donnée interne (ni clé).
 */
public record ConditionResponse(
        CombinaisonCondition combinaison,
        List<RegleConditionResponse> regles
) {
}
