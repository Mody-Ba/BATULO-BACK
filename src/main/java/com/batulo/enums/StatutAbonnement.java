package com.batulo.enums;

/** Statuts d'un abonnement (section 5 du cahier des charges). */
public enum StatutAbonnement {
    /** Inscrit, paiement non effectué : aucun accès aux fonctionnalités. */
    EN_ATTENTE_PAIEMENT,
    /** Paiement validé, date d'expiration non atteinte : accès complet. */
    ACTIF,
    /** 7 jours ou moins avant l'expiration : notification au super admin et à l'admin. */
    EXPIRE_BIENTOT,
    /** Date d'expiration dépassée : accès en lecture seule. */
    EXPIRE,
    /** Désactivé par le super admin : aucun accès pour l'admin et ses gestionnaires. */
    DESACTIVE
}
