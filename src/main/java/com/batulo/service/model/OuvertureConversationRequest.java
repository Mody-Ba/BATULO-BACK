package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Avec qui ouvrir (ou retrouver) la conversation. */
public record OuvertureConversationRequest(
        @Schema(description = "Admin : id d'un de ses gestionnaires, ou absent pour écrire à Batulo. Gestionnaire : "
                + "absent (son admin). Super admin : id d'un admin.", example = "7")
        Long interlocuteurId
) {
}
