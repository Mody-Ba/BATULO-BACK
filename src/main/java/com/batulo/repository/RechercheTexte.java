package com.batulo.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

import java.util.Arrays;

/**
 * Recherche « contient » sans tenir compte des majuscules ni des accents (lower + translate, sans extension
 * unaccent). Le terme doit avoir été passé par NormaliseurRecherche : les deux doivent rester cohérents.
 */
public final class RechercheTexte {

    /** Lettres accentuées (minuscules) et leur équivalent sans accent : même résultat que NormaliseurRecherche. */
    private static final String AVEC_ACCENTS = "àâäáãåéèêëíìîïóòôöõúùûüçñÿý";
    private static final String SANS_ACCENTS = "aaaaaaeeeeiiiiooooouuuucnyy";
    private static final char ECHAPPEMENT = '\\';

    private RechercheTexte() {
    }

    /** Au moins une des colonnes contient le terme normalisé ; % et _ sont cherchés tels quels. */
    @SafeVarargs
    public static Predicate contient(CriteriaBuilder cb, String termeNormalise, Expression<String>... colonnes) {
        String motif = "%" + echapper(termeNormalise) + "%";
        return cb.or(Arrays.stream(colonnes)
                .map(colonne -> cb.like(sansAccents(cb, colonne), motif, ECHAPPEMENT))
                .toArray(Predicate[]::new));
    }

    private static Expression<String> sansAccents(CriteriaBuilder cb, Expression<String> colonne) {
        return cb.function("translate", String.class, cb.lower(colonne), cb.literal(AVEC_ACCENTS),
                cb.literal(SANS_ACCENTS));
    }

    private static String echapper(String terme) {
        return terme.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
