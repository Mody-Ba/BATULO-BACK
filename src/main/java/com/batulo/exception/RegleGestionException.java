package com.batulo.exception;

/**
 * Violation d'une règle de gestion (400) : mot de passe et confirmation différents, etc.
 * Le code précise la règle concernée pour le frontend.
 */
public class RegleGestionException extends RuntimeException {

    private final String code;

    public RegleGestionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
