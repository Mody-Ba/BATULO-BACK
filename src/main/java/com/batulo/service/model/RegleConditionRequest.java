package com.batulo.service.model;

import com.batulo.enums.OperateurCondition;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Règle d'une condition d'affichage : « la question source vérifie opérateur valeur ». */
public record RegleConditionRequest(
        @NotNull(message = "questionId est obligatoire")
        @Schema(description = "Question source, du même sondage, placée avant la cible", example = "12")
        Long questionId,

        @NotNull(message = "l'opérateur est obligatoire")
        @Schema(description = "CHOIX_UNIQUE : EGAL, DIFFERENT, PARMI ; CHOIX_MULTIPLE : CONTIENT, PARMI ; OUI_NON : EGAL, "
                + "DIFFERENT ; NUMERIQUE, ECHELLE, DATE : EGAL, DIFFERENT, SUPERIEUR, INFERIEUR (stricts), ENTRE (bornes "
                + "comprises) ; tous les types : REPONDUE, NON_REPONDUE", example = "EGAL")
        OperateurCondition operateur,

        @Schema(description = "Valeur d'option (jamais le libellé) ; true/false (OUI_NON) ; nombre ; date \"AAAA-MM-JJ\" ; "
                + "liste de valeurs d'options pour PARMI ; [min, max] pour ENTRE ; absente pour REPONDUE et NON_REPONDUE",
                example = "true")
        JsonNode valeur
) {
}
