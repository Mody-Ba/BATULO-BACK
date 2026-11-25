package com.batulo.service.model;

/**
 * Lien personnel du formulaire public : jamais d'identité (ni nom ni courriel).
 *
 * @param dejaRepondu         l'invité a déjà répondu (l'envoi sera refusé : 409 DEJA_REPONDU)
 * @param reponsesNominatives true : la réponse sera associée à l'invité (à signaler au répondant)
 */
public record InvitationPubliqueModel(boolean dejaRepondu, boolean reponsesNominatives) {
}
