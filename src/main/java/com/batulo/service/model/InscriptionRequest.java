package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Formulaire d'inscription de l'admin d'une entreprise (section 3.2 du cahier des charges). */
public record InscriptionRequest(
        @NotBlank(message = "le prénom est obligatoire")
        @Size(max = 100, message = "le prénom ne doit pas dépasser 100 caractères")
        @Schema(example = "Awa")
        String prenom,

        @NotBlank(message = "le nom est obligatoire")
        @Size(max = 100, message = "le nom ne doit pas dépasser 100 caractères")
        @Schema(example = "Diallo")
        String nom,

        @NotBlank(message = "le courriel est obligatoire")
        @Email(message = "le courriel n'est pas valide")
        @Size(max = 255, message = "le courriel ne doit pas dépasser 255 caractères")
        @Schema(example = "awa@entreprise-test.com")
        String courriel,

        @NotBlank(message = "le mot de passe est obligatoire")
        @Size(max = 72, message = "le mot de passe ne doit pas dépasser 72 caractères")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "le mot de passe doit contenir au moins 8 caractères, dont une lettre et un chiffre")
        @Schema(example = "MotDePasse123")
        String motDePasse,

        @NotBlank(message = "la confirmation du mot de passe est obligatoire")
        @Schema(example = "MotDePasse123")
        String confirmationMotDePasse,

        @Size(max = 150, message = "le nom de l'entreprise ne doit pas dépasser 150 caractères")
        @Schema(description = "Facultatif. Par défaut : « Entreprise de Prénom Nom »", example = "Entreprise Test")
        String nomEntreprise
) {
    /** Le toString généré par le record afficherait les mots de passe en clair. */
    @Override
    public String toString() {
        return "InscriptionRequest[prenom=" + prenom + ", nom=" + nom + ", courriel=" + courriel
                + ", motDePasse=****, confirmationMotDePasse=****, nomEntreprise=" + nomEntreprise + "]";
    }
}
