package com.batulo.service.model;

/** Règle commune des mots de passe choisis par l'utilisateur (inscription, changement, réinitialisation). */
public final class ReglesMotDePasse {

    public static final String REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";
    public static final String MESSAGE = "le mot de passe doit contenir au moins 8 caractères, dont une lettre et un chiffre";
    /** Limite de BCrypt : au-delà, les octets suivants seraient ignorés. */
    public static final int TAILLE_MAX = 72;

    private ReglesMotDePasse() {
    }
}
