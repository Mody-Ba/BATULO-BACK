package com.batulo.exception;

/** Publication d'un nouveau sondage sans abonnement actif ni crédit disponible : 402 CREDITS_EPUISES. */
public class CreditsEpuisesException extends RuntimeException {

    public static final String CODE = "CREDITS_EPUISES";

    public CreditsEpuisesException(String message) {
        super(message);
    }

    public String getCode() {
        return CODE;
    }
}
