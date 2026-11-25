package com.batulo.service.model;

/**
 * Formulaire ouvert avec un lien personnel. Jamais d'identité de l'invité.
 *
 * @param dejaRepondu         l'invité a déjà répondu : l'envoi sera refusé (409 DEJA_REPONDU)
 * @param reponsesNominatives true : la réponse sera associée à la personne invitée (nom et courriel visibles par
 *                            l'organisateur) ; à indiquer au répondant avant qu'il réponde. false : réponse anonyme.
 */
public record InvitationPubliqueResponse(boolean dejaRepondu, boolean reponsesNominatives) {
}
