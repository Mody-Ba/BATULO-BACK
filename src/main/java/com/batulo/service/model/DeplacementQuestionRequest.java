package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Déplacement d'une question vers une section du même sondage. */
public record DeplacementQuestionRequest(
        @NotNull(message = "la section d'arrivée est obligatoire")
        @Schema(example = "12")
        Long sectionId,

        @Min(value = 1, message = "la position commence à 1")
        @Schema(description = "Rang dans la section d'arrivée, à partir de 1 ; absent : à la fin", example = "2")
        Integer position
) {
}
