package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Création ou modification d'un sondage (titre et description). */
public record SondageRequest(
        @NotBlank(message = "le titre est obligatoire")
        @Size(max = 200, message = "le titre ne doit pas dépasser 200 caractères")
        @Schema(example = "Enquête agricole – campagne 2026")
        String titre,

        @Size(max = 5000, message = "la description ne doit pas dépasser 5000 caractères")
        @Schema(example = "Suivi des exploitations du bassin arachidier")
        String description,

        @Schema(description = "true (défaut) : une seule réponse par appareil (sondage en ligne) ; false : collecte "
                + "terrain, plusieurs réponses depuis le même appareil. Modifiable dans le brouillon, figé à la publication.",
                example = "true")
        Boolean reponseUnique
) {
}
