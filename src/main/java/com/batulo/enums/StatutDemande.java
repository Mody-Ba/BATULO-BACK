package com.batulo.enums;

/** Statut d'une demande d'augmentation d'abonnement (SA-07). */
public enum StatutDemande {
    EN_ATTENTE,
    /** Acceptée : l'admin paie lui-même la formule par le parcours Stripe (RG-09 : pas d'abonnement manuel). */
    TRAITEE,
    REFUSEE
}
