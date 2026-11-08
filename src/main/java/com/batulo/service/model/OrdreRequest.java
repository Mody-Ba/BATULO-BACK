package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Nouvel ordre après un glisser-déposer : TOUS les ids, chacun une seule fois. */
public record OrdreRequest(
        @NotEmpty(message = "la liste des ids est obligatoire")
        @Size(max = 100, message = "la liste des ids ne doit pas dépasser 100 éléments")
        @Schema(example = "[32, 31]")
        List<@NotNull(message = "id vide") Long> ids
) {
}
