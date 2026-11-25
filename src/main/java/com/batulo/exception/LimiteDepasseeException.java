package com.batulo.exception;

/** Quota dépassé (ex. courriels d'invitation par heure et par entreprise) : 429. */
public class LimiteDepasseeException extends RuntimeException {

    private final String code;

    public LimiteDepasseeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
