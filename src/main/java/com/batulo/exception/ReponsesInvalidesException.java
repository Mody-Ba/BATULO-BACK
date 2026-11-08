package com.batulo.exception;

import java.util.List;

/** Réponses à un sondage refusées (400 REPONSES_INVALIDES), avec une erreur par question. */
public class ReponsesInvalidesException extends RuntimeException {

    public static final String CODE = "REPONSES_INVALIDES";

    private final List<ErreurQuestion> erreurs;

    public ReponsesInvalidesException(List<ErreurQuestion> erreurs) {
        super(erreurs.size() == 1 ? "1 réponse est invalide." : erreurs.size() + " réponses sont invalides.");
        this.erreurs = List.copyOf(erreurs);
    }

    public List<ErreurQuestion> getErreurs() {
        return erreurs;
    }
}
