package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Autoriser ou retirer la création de sondages à un gestionnaire (AD-09). */
public record PermissionSondagesRequest(
        @NotNull(message = "peutCreerSondages est obligatoire (true ou false)")
        @Schema(example = "true")
        Boolean peutCreerSondages
) {
}
