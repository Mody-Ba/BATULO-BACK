package com.batulo.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Opérateur d'une règle de condition d'affichage. Les opérateurs permis dépendent du type de la question source
 * ({@link #permis(TypeQuestion)}) ; une source sans réponse (ou masquée) ne vérifie que NON_REPONDUE.
 */
public enum OperateurCondition {
    /** Valeur d'option (CHOIX_UNIQUE), booléen (OUI_NON), nombre (NUMERIQUE, ECHELLE) ou date. */
    EGAL,
    /** Contraire de EGAL, pour une source répondue. */
    DIFFERENT,
    /** Liste de valeurs d'options : l'une d'elles est choisie (CHOIX_UNIQUE) ou cochée (CHOIX_MULTIPLE). */
    PARMI,
    /** CHOIX_MULTIPLE : cette option est cochée. */
    CONTIENT,
    /** Strictement supérieur (NUMERIQUE, ECHELLE, DATE : postérieure). */
    SUPERIEUR,
    /** Strictement inférieur (NUMERIQUE, ECHELLE, DATE : antérieure). */
    INFERIEUR,
    /** [min, max], bornes comprises (NUMERIQUE, ECHELLE, DATE). */
    ENTRE,
    /** La source a reçu une réponse (tous les types). */
    REPONDUE,
    /** La source n'a pas reçu de réponse, ou elle était masquée (tous les types). */
    NON_REPONDUE;

    private static final Set<OperateurCondition> COMPARAISONS = EnumSet.of(EGAL, DIFFERENT, SUPERIEUR, INFERIEUR, ENTRE);

    /** Opérateurs permis pour une question source de ce type. */
    public static Set<OperateurCondition> permis(TypeQuestion type) {
        Set<OperateurCondition> permis = EnumSet.of(REPONDUE, NON_REPONDUE);
        switch (type) {
            case CHOIX_UNIQUE -> permis.addAll(EnumSet.of(EGAL, DIFFERENT, PARMI));
            case CHOIX_MULTIPLE -> permis.addAll(EnumSet.of(CONTIENT, PARMI));
            case OUI_NON -> permis.addAll(EnumSet.of(EGAL, DIFFERENT));
            case NUMERIQUE, ECHELLE, DATE -> permis.addAll(COMPARAISONS);
            default -> {
            }
        }
        return permis;
    }

    /** REPONDUE et NON_REPONDUE ne prennent pas de valeur. */
    public boolean sansValeur() {
        return this == REPONDUE || this == NON_REPONDUE;
    }
}
