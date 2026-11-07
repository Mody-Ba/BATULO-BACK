package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Changement de mot de passe par l'utilisateur connecté (obligatoire après un mot de passe temporaire). */
public record ChangementMotDePasseRequest(
        @NotBlank(message = "le mot de passe actuel est obligatoire")
        @Size(max = ReglesMotDePasse.TAILLE_MAX, message = "le mot de passe ne doit pas dépasser 72 caractères")
        String motDePasseActuel,

        @NotBlank(message = "le nouveau mot de passe est obligatoire")
        @Size(max = ReglesMotDePasse.TAILLE_MAX, message = "le mot de passe ne doit pas dépasser 72 caractères")
        @Pattern(regexp = ReglesMotDePasse.REGEX, message = ReglesMotDePasse.MESSAGE)
        @Schema(example = "NouveauMotDePasse1")
        String nouveauMotDePasse,

        @NotBlank(message = "la confirmation du mot de passe est obligatoire")
        @Schema(example = "NouveauMotDePasse1")
        String confirmationMotDePasse
) {
    @Override
    public String toString() {
        return "ChangementMotDePasseRequest[****]";
    }
}
