package com.batulo.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Réponse à une question. « valeur » selon le type : CHOIX_UNIQUE "valeur_option" ; CHOIX_MULTIPLE et CLASSEMENT
 * ["valeur_option", ...] ; OUI_NON true/false ; ECHELLE et NUMERIQUE un nombre ; TEXTE_* un texte ; DATE "AAAA-MM-JJ" ;
 * MATRICE {"ligne": "colonne", ...}. Absente ou null : pas de réponse.
 */
public record ReponseQuestionRequest(
        @NotNull(message = "questionId est obligatoire")
        @Schema(example = "101")
        Long questionId,

        @Schema(example = "\"kaolack\"")
        JsonNode valeur
) {
}
