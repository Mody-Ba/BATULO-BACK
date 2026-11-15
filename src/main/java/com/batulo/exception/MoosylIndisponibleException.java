package com.batulo.exception;

/** Moosyl a refusé la demande ou ne répond pas (502) : l'abonnement n'est pas modifié. */
public class MoosylIndisponibleException extends RuntimeException {

    public static final String CODE = "MOOSYL_ERREUR";

    public MoosylIndisponibleException(String message) {
        super(message);
    }
}
