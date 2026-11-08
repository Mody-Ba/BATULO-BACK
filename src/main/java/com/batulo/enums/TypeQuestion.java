package com.batulo.enums;

/** Les 10 types de questions du formulaire dynamique (module 4.1 du cahier des charges). */
public enum TypeQuestion {
    CHOIX_UNIQUE(true),
    CHOIX_MULTIPLE(true),
    OUI_NON(false),
    TEXTE_COURT(false),
    TEXTE_LONG(false),
    ECHELLE(false),
    CLASSEMENT(true),
    NUMERIQUE(false),
    DATE(false),
    MATRICE(false);

    private final boolean avecOptions;

    TypeQuestion(boolean avecOptions) {
        this.avecOptions = avecOptions;
    }

    /** true : la question a une liste d'options (au moins 2) ; false : aucune option. */
    public boolean isAvecOptions() {
        return avecOptions;
    }
}
