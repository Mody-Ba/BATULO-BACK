package com.batulo.enums;

/**
 * Statut d'une invitation, calculé (jamais stocké) dans cet ordre de priorité : REVOQUEE, A_REPONDU, OUVERTE,
 * EN_ECHEC, INVITEE, A_INVITER. Le calcul Java (RegleInvitation) et sa traduction SQL (InvitationSpecifications)
 * suivent le même ordre.
 */
public enum StatutInvitation {
    /** Jamais envoyée (ou envoi encore en file). */
    A_INVITER,
    /** Courriel envoyé. */
    INVITEE,
    /** Formulaire ouvert avec le lien personnel, sans réponse. */
    OUVERTE,
    A_REPONDU,
    /** Lien désactivé par l'admin ou le gestionnaire. */
    REVOQUEE,
    /** Dernier envoi en échec (adresse refusée, serveur de courriel indisponible...). */
    EN_ECHEC
}
