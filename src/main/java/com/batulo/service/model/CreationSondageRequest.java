package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Création d'un sondage, vide ou à partir d'un formulaire existant de l'entreprise (réutilisation). */
public record CreationSondageRequest(
        @NotBlank(message = "le titre est obligatoire")
        @Size(max = 200, message = "le titre ne doit pas dépasser 200 caractères")
        @Schema(example = "Enquête agricole – campagne 2027")
        String titre,

        @Size(max = 5000, message = "la description ne doit pas dépasser 5000 caractères")
        @Schema(description = "Facultative ; absente avec une source : celle de la source est reprise")
        String description,

        @Schema(description = "Facultatif : id d'un sondage de l'entreprise (quel que soit son statut) dont les sections, "
                + "questions et options sont copiées, avec les mêmes valeurs d'options. Absent : sondage vide.",
                example = "12")
        Long sondageSourceId,

        @Schema(description = "Facultatif. true (défaut, ou valeur de la source) : une seule réponse par appareil (sondage en ligne) ; false : collecte "
                + "terrain, plusieurs réponses depuis le même appareil. Modifiable dans le brouillon, figé à la publication.",
                example = "true")
        Boolean reponseUnique
) {
}
