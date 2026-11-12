package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Texte brut, sans pièce jointe. */
public record MessageRequest(
        @NotBlank(message = "le message est vide")
        @Size(max = 5000, message = "le message ne doit pas dépasser 5000 caractères")
        @Schema(example = "Bonjour, où en est la collecte à Kaolack ?")
        String contenu
) {
}
