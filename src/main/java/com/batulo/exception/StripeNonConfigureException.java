package com.batulo.exception;

/** Clé Stripe absente ou qui n'est pas une clé de TEST (503) : le paiement en ligne est indisponible. */
public class StripeNonConfigureException extends RuntimeException {

    private final String code;

    public StripeNonConfigureException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
