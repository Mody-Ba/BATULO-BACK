package com.batulo.exception;

/** Un compte existe déjà avec ce courriel (409). */
public class CompteExistantException extends RuntimeException {

    public static final String CODE = "COURRIEL_DEJA_UTILISE";

    public CompteExistantException() {
        super("Un compte existe déjà avec ce courriel.");
    }
}
