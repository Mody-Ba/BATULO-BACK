package com.batulo.service.model;

import com.batulo.enums.TypeQuestion;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record QuestionInfoModel(
        String libelle,
        String aide,
        TypeQuestion type,
        boolean obligatoire,
        /** Null si absente. */
        JsonNode configuration,
        List<OptionInfoModel> options,
        /** Null : inchangé. */
        Boolean estCritere,
        JsonNode critereConfig
) {
}
