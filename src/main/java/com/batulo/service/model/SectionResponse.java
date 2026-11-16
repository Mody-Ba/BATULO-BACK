package com.batulo.service.model;

import java.util.List;

public record SectionResponse(
        Long id,
        String titre,
        String description,
        int ordre,
        /** Condition d'affichage ; null : toujours affichée. Masquée, la section masque toutes ses questions. */
        ConditionResponse condition,
        List<QuestionResponse> questions
) {
}
