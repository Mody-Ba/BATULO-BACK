package com.batulo.service.model;

/** Invité associé à une réponse : seulement si le sondage associe les réponses aux personnes invitées. */
public record InviteResponse(Long invitationId, String prenom, String nom, String courriel) {
}
