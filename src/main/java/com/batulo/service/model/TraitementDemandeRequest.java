package com.batulo.service.model;

import com.batulo.enums.StatutDemande;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Réponse du super admin à une demande (SA-07). Sans effet sur l'abonnement (RG-09). */
public record TraitementDemandeRequest(
        @NotNull(message = "le statut est obligatoire (TRAITEE ou REFUSEE)")
        @Schema(example = "TRAITEE")
        StatutDemande statut,

        @Size(max = 2000, message = "la réponse ne doit pas dépasser 2000 caractères")
        @Schema(description = "Obligatoire si le statut est REFUSEE", example = "D'accord : choisissez la formule annuelle depuis la page Abonnement.")
        String reponse
) {
}
