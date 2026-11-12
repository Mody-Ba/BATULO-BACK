package com.batulo.enums;

/** Types de notifications du tableau de bord (section 5 du cahier des charges). */
public enum TypeNotification {
    /** Super admins : un admin s'est inscrit. */
    NOUVELLE_INSCRIPTION,
    /** Super admins : un abonnement a été payé ou renouvelé. */
    PAIEMENT_RECU,
    /** Admin et super admins : l'abonnement expire dans 7 jours ou moins. */
    ABONNEMENT_EXPIRE_BIENTOT,
    /** Admin et super admins : la date d'expiration est dépassée. */
    ABONNEMENT_EXPIRE,
    /** Gestionnaire : un sondage lui a été assigné (tableau de bord + courriel). */
    SONDAGE_ASSIGNE,
    /** Gestionnaire : une assignation lui a été retirée (tableau de bord seulement). */
    SONDAGE_DESASSIGNE,
    /** Destinataire : premier message non lu d'une conversation. */
    NOUVEAU_MESSAGE,
    /** Admin : un gestionnaire lui a envoyé les statistiques d'un sondage terminé (GE-06). */
    STATISTIQUES_RECUES,
    /** Super admins : un admin demande une augmentation d'abonnement (AD-05, SA-07). */
    DEMANDE_ABONNEMENT,
    /** Admin : sa demande d'augmentation a été traitée ou refusée. */
    DEMANDE_ABONNEMENT_TRAITEE
}
