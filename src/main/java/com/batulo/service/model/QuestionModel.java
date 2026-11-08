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
        List<OptionModel> options
) {
}
