package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Modification d'un gestionnaire par l'admin (AD-08). */
public record ModificationGestionnaireRequest(
        @NotBlank(message = "le prénom est obligatoire")
        @Size(max = 100, message = "le prénom ne doit pas dépasser 100 caractères")
        @Schema(example = "Moussa")
        String prenom,

        @NotBlank(message = "le nom est obligatoire")
        @Size(max = 100, message = "le nom ne doit pas dépasser 100 caractères")
        @Schema(example = "Diop")
        String nom,

        @NotBlank(message = "le courriel est obligatoire")
        @Email(message = "le courriel n'est pas valide")
        @Size(max = 255, message = "le courriel ne doit pas dépasser 255 caractères")
        @Schema(example = "moussa.diop@entreprise-test.com")
        String courriel
) {
}
