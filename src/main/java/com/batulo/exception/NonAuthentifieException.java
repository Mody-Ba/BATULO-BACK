package com.batulo.exception;

/** Identifiants ou jeton refusés (401) : mauvais mot de passe, jeton de rafraîchissement invalide... */
public class NonAuthentifieException extends RuntimeException {

    private final String code;

    public NonAuthentifieException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
