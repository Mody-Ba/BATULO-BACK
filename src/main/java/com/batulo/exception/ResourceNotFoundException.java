package com.batulo.exception;

/** Ressource introuvable (404) : abonnement, paiement... Le code précise laquelle pour le frontend. */
public class ResourceNotFoundException extends RuntimeException {

    private final String code;

    public ResourceNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
