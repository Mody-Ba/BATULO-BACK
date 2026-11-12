package com.batulo.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Terme d'une recherche textuelle, comparé sans tenir compte des majuscules ni des accents. La colonne est
 * normalisée en SQL par {@link com.batulo.repository.GestionnaireSpecifications} (lower + translate) : les deux
 * doivent rester cohérents.
 */
public final class NormaliseurRecherche {

    private NormaliseurRecherche() {
    }

    /** @return le terme en minuscules et sans accents, ou null s'il est absent ou vide */
    public static String normaliser(String terme) {
        if (terme == null || terme.isBlank()) {
            return null;
        }
        String sansAccents = Normalizer.normalize(terme.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sansAccents.toLowerCase(Locale.ROOT);
    }
}
