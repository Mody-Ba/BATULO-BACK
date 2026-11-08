package com.batulo.service.model;

import java.util.List;

public record SectionResponse(
        Long id,
        String titre,
        String description,
        int ordre,
        List<QuestionResponse> questions
) {
}
