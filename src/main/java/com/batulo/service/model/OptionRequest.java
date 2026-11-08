package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Option d'une question à choix. Avec « id » : option existante (son libellé change, sa valeur reste celle
 * enregistrée si « valeur » est absente). Sans « id » : nouvelle option.
 */
public record OptionRequest(
        @Schema(description = "Id d'une option existante de la question ; absent pour une nouvelle option")
        Long id,

        @NotBlank(message = "le libellé de l'option est obligatoire")
        @Size(max = 200, message = "le libellé de l'option ne doit pas dépasser 200 caractères")
        @Schema(example = "Arachide")
        String libelle,

        @Size(max = 100, message = "la valeur de l'option ne doit pas dépasser 100 caractères")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$",
                message = "la valeur de l'option ne peut contenir que des lettres sans accent, chiffres, « _ », « - » et « . »")
        @Schema(description = "Code stable pour les statistiques. Facultatif : généré depuis le libellé "
                + "(« Maïs » donne « mais »), jamais régénéré ensuite", example = "arachide")
        String valeur
) {
}
