package com.batulo.service.model;

/** Invité associé à une réponse (sondage nominatif seulement). */
public record InviteModel(Long invitationId, String prenom, String nom, String courriel) {
}
