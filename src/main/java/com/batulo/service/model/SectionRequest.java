package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Création ou modification d'une section. */
public record SectionRequest(
        @NotBlank(message = "le titre est obligatoire")
        @Size(max = 200, message = "le titre ne doit pas dépasser 200 caractères")
        @Schema(example = "Exploitation")
        String titre,

        @Size(max = 5000, message = "la description ne doit pas dépasser 5000 caractères")
        @Schema(example = "Informations générales")
        String description
) {
}
