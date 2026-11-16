package com.batulo.service.model;

import com.batulo.enums.CombinaisonCondition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Condition d'affichage : la cible n'est affichée que si ses règles sont vérifiées (toutes : ET ; une : OU). */
public record ConditionRequest(
        @Schema(description = "ET (défaut) : toutes les règles ; OU : au moins une", example = "ET")
        CombinaisonCondition combinaison,

        @NotEmpty(message = "une condition a au moins une règle")
        @Size(max = 10, message = "une condition ne peut pas avoir plus de 10 règles")
        List<@Valid @NotNull(message = "règle vide") RegleConditionRequest> regles
) {
}
