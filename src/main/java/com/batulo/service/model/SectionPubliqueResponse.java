package com.batulo.service.model;

import java.util.List;

/** Section du formulaire public : uniquement ce qu'il faut pour répondre. */
public record SectionPubliqueResponse(
        Long id,
        String titre,
        String description,
        int ordre,
        List<QuestionPubliqueResponse> questions
) {
}
