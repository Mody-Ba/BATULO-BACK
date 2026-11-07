package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Modification d'un admin par le super admin (SA-02) : identité, courriel et nom de l'entreprise. */
public record ModificationAdminRequest(
        @NotBlank(message = "le prénom est obligatoire")
        @Size(max = 100, message = "le prénom ne doit pas dépasser 100 caractères")
        @Schema(example = "Fatou")
        String prenom,

        @NotBlank(message = "le nom est obligatoire")
        @Size(max = 100, message = "le nom ne doit pas dépasser 100 caractères")
        @Schema(example = "Sow")
        String nom,

        @NotBlank(message = "le courriel est obligatoire")
        @Email(message = "le courriel n'est pas valide")
        @Size(max = 255, message = "le courriel ne doit pas dépasser 255 caractères")
        @Schema(example = "fatou.sow@entreprise-sa.com")
        String courriel,

        @NotBlank(message = "le nom de l'entreprise est obligatoire")
        @Size(max = 150, message = "le nom de l'entreprise ne doit pas dépasser 150 caractères")
        @Schema(example = "Entreprise SA Renommée")
        String nomEntreprise
) {
}
