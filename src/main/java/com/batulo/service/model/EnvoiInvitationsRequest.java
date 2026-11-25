package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Sélection d'invités ; absente ou vide : tous ceux qui sont concernés. */
public record EnvoiInvitationsRequest(
        @Size(max = 10000, message = "trop d'invitations")
        @Schema(description = "Ids des invitations ; absent : toutes celles concernées")
        List<Long> invitationIds
) {
}
