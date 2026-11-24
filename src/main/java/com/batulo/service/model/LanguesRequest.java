package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Langues d'un brouillon : la langue principale (celle de la saisie) et les langues supplémentaires. */
public record LanguesRequest(
        @NotBlank(message = "la langue principale est obligatoire")
        @Schema(description = "fr, en ou ar. La changer ne change que l'étiquette : les textes saisis ne sont pas "
                + "traduits ni échangés", example = "fr")
        String languePrincipale,

        @Size(max = 2, message = "deux langues supplémentaires au plus")
        @Schema(description = "Codes des langues supplémentaires (sans la principale) ; absent ou [] : aucune. "
                + "Les traductions d'une langue retirée sont gardées mais ignorées", example = "[\"en\", \"ar\"]")
        List<String> languesSupplementaires
) {
}
