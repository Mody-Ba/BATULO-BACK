package com.batulo.exception;

/** La ressource n'est plus disponible (410) : sondage terminé, fermé, ou collecte fermée. */
public class RessourceFermeeException extends RuntimeException {

    private final String code;

    public RessourceFermeeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
