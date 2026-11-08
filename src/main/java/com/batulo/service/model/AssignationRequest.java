package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Gestionnaires de l'entreprise à qui assigner le sondage (toutes ses versions). */
public record AssignationRequest(
        @NotEmpty(message = "la liste des gestionnaires est obligatoire")
        @Size(max = 100, message = "pas plus de 100 gestionnaires à la fois")
        @Schema(example = "[7, 9]")
        List<@NotNull(message = "id vide") Long> gestionnaireIds
) {
}
