package com.batulo.exception;

/** Action refusée quel que soit l'appelant (403), par exemple modifier le super admin. */
public class ActionInterditeException extends RuntimeException {

    private final String code;

    public ActionInterditeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
