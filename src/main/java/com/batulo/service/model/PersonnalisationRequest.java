package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Réglages de l'apparence. Champ absent : inchangé ; "" : effacé (retour à la valeur par défaut). */
public record PersonnalisationRequest(
        @Size(max = 150, message = "le nom affiché ne doit pas dépasser 150 caractères")
        @Schema(description = "Nom montré aux répondants et dans les rapports (sinon le nom de l'entreprise)",
                example = "Coopérative de Kaolack")
        String nomAffiche,

        @Schema(description = "Couleur principale #RRGGBB (sinon couleur Batulo par défaut) ; 400 COULEUR_INVALIDE",
                example = "#1E6B52")
        String couleurPrincipale
) {
}
