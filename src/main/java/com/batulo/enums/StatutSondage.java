package com.batulo.enums;

/** Statuts d'un sondage (cahier des charges), calculés à partir des dates de publication et de fermeture. */
public enum StatutSondage {
    /** Brouillon, ou publication planifiée pas encore atteinte (le champ « publie » distingue les deux). */
    NON_COMMENCE,
    /** Publié, ouvert, pas encore fermé : il peut recevoir des réponses. */
    EN_COURS,
    /** Fermé à la main, ou date de fermeture dépassée. */
    TERMINE
}
