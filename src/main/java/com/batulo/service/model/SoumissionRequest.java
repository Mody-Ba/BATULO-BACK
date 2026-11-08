package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Réponses d'un répondant (sans compte). */
public record SoumissionRequest(
        @Size(max = 100, message = "jetonRepondant invalide")
        @Schema(description = "UUID généré et gardé par le navigateur ; obligatoire si le sondage n'accepte qu'une "
                + "réponse par appareil", example = "3f6c2a1e-8b1d-4c3e-9a77-2f0e5b4d9c11")
        String jetonRepondant,

        @NotNull(message = "la liste des réponses est obligatoire")
        @Size(max = 5000, message = "trop de réponses")
        List<@Valid @NotNull(message = "réponse vide") ReponseQuestionRequest> reponses
) {
}
