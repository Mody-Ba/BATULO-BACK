package com.batulo.exception;

import java.util.List;

/** Publication refusée : une langue supplémentaire n'est pas complète (400, liste des textes manquants). */
public class TraductionsIncompletesException extends RuntimeException {

    public static final String CODE = "TRADUCTIONS_INCOMPLETES";

    private final List<TexteManquant> manquants;

    public TraductionsIncompletesException(String message, List<TexteManquant> manquants) {
        super(message);
        this.manquants = List.copyOf(manquants);
    }

    public List<TexteManquant> getManquants() {
        return manquants;
    }
}
