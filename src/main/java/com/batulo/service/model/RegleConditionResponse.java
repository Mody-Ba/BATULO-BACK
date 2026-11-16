package com.batulo.service.model;

import com.batulo.enums.OperateurCondition;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/** Règle d'une condition d'affichage ; la source est désignée par son id dans cette version. */
public record RegleConditionResponse(
        Long questionId,
        OperateurCondition operateur,
        /** Absente pour REPONDUE et NON_REPONDUE. */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        JsonNode valeur
) {
}
