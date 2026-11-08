package com.batulo.enums;

/** Nature d'un message ; les messages automatiques portent un referenceId (envoi ou demande). */
public enum TypeMessage {
    TEXTE,
    /** Envoi des statistiques d'un sondage terminé (GE-06). */
    STATISTIQUES,
    /** Demande d'augmentation d'abonnement (AD-05). */
    DEMANDE_ABONNEMENT,
    /** Réponse du super admin à une demande (SA-07). */
    REPONSE_DEMANDE
}
