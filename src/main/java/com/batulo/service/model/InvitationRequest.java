package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Une personne invitée : courriel obligatoire (unique dans le sondage), le reste facultatif. */
public record InvitationRequest(
        @Size(max = 100, message = "le prénom ne doit pas dépasser 100 caractères")
        @Schema(example = "Aminata")
        String prenom,

        @Size(max = 100, message = "le nom ne doit pas dépasser 100 caractères")
        @Schema(example = "Diallo")
        String nom,

        @NotBlank(message = "le courriel est obligatoire")
        @Size(max = 254, message = "le courriel ne doit pas dépasser 254 caractères")
        @Schema(example = "aminata.diallo@exemple.com")
        String courriel,

        @Size(max = 30, message = "le téléphone ne doit pas dépasser 30 caractères")
        @Schema(description = "Facultatif, pour une diffusion par SMS plus tard", example = "+222 22 33 44 55")
        String telephone,

        @Size(max = 10, message = "langue invalide")
        @Schema(description = "Langue préférée (fr, en, ar) : langue du courriel et du formulaire si le sondage la "
                + "propose ; absente : langue principale du sondage", example = "ar")
        String langue
) {
}
