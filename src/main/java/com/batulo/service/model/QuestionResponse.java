package com.batulo.service.model;

import com.batulo.enums.TypeQuestion;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record QuestionResponse(
        Long id,
        String libelle,
        String aide,
        TypeQuestion type,
        boolean obligatoire,
        int ordre,
        JsonNode configuration,
        List<OptionResponse> options,
        /** Condition d'affichage ; null : toujours affichée (si sa section l'est). */
        ConditionResponse condition,
        /** Absent du formulaire public (réglage d'analyse interne). */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean estCritere,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        JsonNode critereConfig
) {
}
