package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConnexionRequest(
        @NotBlank(message = "le courriel est obligatoire")
        @Size(max = 255, message = "le courriel ne doit pas dépasser 255 caractères")
        @Schema(example = "awa@entreprise-test.com")
        String courriel,

        @NotBlank(message = "le mot de passe est obligatoire")
        @Size(max = ReglesMotDePasse.TAILLE_MAX, message = "le mot de passe ne doit pas dépasser 72 caractères")
        @Schema(example = "MotDePasse123")
        String motDePasse
) {
    @Override
    public String toString() {
        return "ConnexionRequest[courriel=" + courriel + ", motDePasse=****]";
    }
}
