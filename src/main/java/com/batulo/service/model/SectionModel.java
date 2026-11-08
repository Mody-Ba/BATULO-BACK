package com.batulo.service.model;

import java.util.List;

public record SectionModel(
        Long id,
        String titre,
        String description,
        int ordre,
        List<QuestionModel> questions
) {
}
