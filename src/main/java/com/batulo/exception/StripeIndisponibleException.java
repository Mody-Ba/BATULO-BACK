package com.batulo.exception;

/** Stripe a refusé la demande ou n'a pas répondu (502). Le détail technique reste dans les logs. */
public class StripeIndisponibleException extends RuntimeException {

    public static final String CODE = "STRIPE_ERREUR";

    public StripeIndisponibleException(String message) {
        super(message);
    }
}
