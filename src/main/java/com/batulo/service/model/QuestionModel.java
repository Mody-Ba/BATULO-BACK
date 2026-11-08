package com.batulo.service.model;

import com.batulo.enums.TypeQuestion;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record QuestionModel(
        Long id,
        String libelle,
        String aide,
        TypeQuestion type,
        boolean obligatoire,
        int ordre,
        JsonNode configuration,
        List<OptionModel> options,
        boolean estCritere,
        /** Null si ce n'est pas un critère, ou s'il n'a pas de découpage. */
        JsonNode critereConfig
) {
}
