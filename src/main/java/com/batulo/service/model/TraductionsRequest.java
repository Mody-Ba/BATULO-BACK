package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Traductions d'une langue, enregistrées en une fois : un texte absent de la liste reste inchangé. */
public record TraductionsRequest(
        @NotNull(message = "la liste des textes est obligatoire")
        @Size(max = 10000, message = "trop de textes")
        List<@Valid @NotNull(message = "texte vide") Texte> textes
) {
    public record Texte(
            @NotBlank(message = "la clé du texte est obligatoire")
            @Size(max = 100, message = "clé invalide")
            @Schema(description = "Clé donnée par GET .../traductions/{langue}", example = "question.12.libelle")
            String cle,

            @Size(max = 10000, message = "traduction trop longue")
            @Schema(description = "Traduction ; null ou vide : traduction effacée", example = "How satisfied are you?")
            String traduction
    ) {
    }
}
