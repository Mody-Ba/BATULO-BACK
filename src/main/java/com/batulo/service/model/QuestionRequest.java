package com.batulo.service.model;

import com.batulo.enums.TypeQuestion;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Création ou modification d'une question. En modification, la liste d'options remplace l'ancienne :
 * les options absentes sont supprimées.
 */
public record QuestionRequest(
        @NotBlank(message = "le libellé est obligatoire")
        @Size(max = 500, message = "le libellé ne doit pas dépasser 500 caractères")
        @Schema(example = "Cultures pratiquées")
        String libelle,

        @Size(max = 500, message = "l'aide ne doit pas dépasser 500 caractères")
        @Schema(example = "Plusieurs réponses possibles")
        String aide,

        @NotNull(message = "le type est obligatoire")
        @Schema(example = "CHOIX_MULTIPLE")
        TypeQuestion type,

        @Schema(description = "false par défaut", example = "true")
        Boolean obligatoire,

        @Schema(description = "Selon le type : ECHELLE {min,max,libelleMin,libelleMax}, NUMERIQUE {min,max,decimales}, "
                + "TEXTE_COURT/TEXTE_LONG {longueurMax}, DATE {dateMin,dateMax}, MATRICE {lignes,colonnes} ; {} sinon",
                example = "{}")
        JsonNode configuration,

        @Size(max = 50, message = "une question ne peut pas avoir plus de 50 options")
        @Schema(description = "Au moins 2 pour CHOIX_UNIQUE, CHOIX_MULTIPLE et CLASSEMENT ; aucune pour les autres types")
        List<@Valid @NotNull(message = "option vide") OptionRequest> options
) {
}
