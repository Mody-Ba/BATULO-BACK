package com.batulo.service.model;

import com.batulo.enums.CombinaisonCondition;
import com.batulo.enums.OperateurCondition;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** Condition d'affichage d'une question ou d'une section ; la source d'une règle est désignée par son id. */
public record ConditionModel(CombinaisonCondition combinaison, List<Regle> regles) {

    /** @param questionId question source, dans le même sondage ; null si elle n'y est plus (ne devrait pas arriver) */
    public record Regle(Long questionId, OperateurCondition operateur, JsonNode valeur) {
    }
}
