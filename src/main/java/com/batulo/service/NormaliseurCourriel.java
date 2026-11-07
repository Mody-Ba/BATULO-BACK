package com.batulo.service;

import java.util.Locale;

/** Point unique de normalisation : les courriels sont stockés et recherchés en minuscules. */
public final class NormaliseurCourriel {

    private NormaliseurCourriel() {
    }

    public static String normaliser(String courriel) {
        return courriel.trim().toLowerCase(Locale.ROOT);
    }
}
