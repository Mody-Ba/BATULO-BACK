package com.batulo.exception;

/**
 * Action impossible dans l'état actuel de la ressource (409) : modifier un sondage publié, fermer un sondage déjà
 * terminé... Le code précise le cas pour le frontend.
 */
public class ConflitException extends RuntimeException {

    private final String code;

    public ConflitException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
