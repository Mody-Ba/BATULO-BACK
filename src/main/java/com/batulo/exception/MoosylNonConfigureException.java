package com.batulo.exception;

/** Paiement mobile non configuré sur ce serveur (clé Moosyl absente) : 503. */
public class MoosylNonConfigureException extends RuntimeException {

    public static final String CODE = "PAIEMENT_MOBILE_NON_CONFIGURE";

    public MoosylNonConfigureException(String message) {
        super(message);
    }
}
