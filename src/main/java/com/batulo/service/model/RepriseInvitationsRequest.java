package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** Reprendre la liste des invités d'un autre sondage de l'entreprise (identité seulement). */
public record RepriseInvitationsRequest(
        @NotNull(message = "sondageSourceId est obligatoire")
        @Schema(description = "N'importe quelle version d'un sondage visible de l'entreprise", example = "12")
        Long sondageSourceId
) {
}
