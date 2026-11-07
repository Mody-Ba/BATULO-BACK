package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Nouveau mot de passe choisi à partir du lien « mot de passe oublié ». */
public record ReinitialisationMotDePasseRequest(
        @NotBlank(message = "le jeton est obligatoire")
        @Size(max = 100, message = "jeton invalide")
        @Schema(description = "Jeton reçu dans le lien du courriel (paramètre « jeton »)")
        String jeton,

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
        return "ReinitialisationMotDePasseRequest[jeton=****, nouveauMotDePasse=****, confirmationMotDePasse=****]";
    }
}
