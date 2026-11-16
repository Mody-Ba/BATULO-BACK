package com.batulo.service.model;

import java.util.List;

public record SectionModel(
        Long id,
        String titre,
        String description,
        int ordre,
        /** Condition d'affichage ; null : toujours affichée. */
        ConditionModel condition,
        List<QuestionModel> questions
) {
}
