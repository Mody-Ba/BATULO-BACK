package com.batulo.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Couleurs des formulaires : format #RRGGBB et couleur de texte lisible sur la couleur principale, choisie entre
 * blanc et noir selon le rapport de contraste WCAG 2.x (luminance relative, (L1 + 0,05) / (L2 + 0,05)).
 */
public final class ContrasteCouleur {

    public static final String BLANC = "#FFFFFF";
    public static final String NOIR = "#000000";

    private static final Pattern FORMAT = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private ContrasteCouleur() {
    }

    public static boolean valide(String couleur) {
        return couleur != null && FORMAT.matcher(couleur).matches();
    }

    /** #rrggbb -> #RRGGBB. */
    public static String normaliser(String couleur) {
        return couleur.toUpperCase(Locale.ROOT);
    }

    /** Blanc ou noir : celui qui contraste le plus avec le fond (à égalité, blanc). */
    public static String couleurTexte(String fond) {
        double l = luminance(fond);
        return rapport(1.0, l) >= rapport(l, 0.0) ? BLANC : NOIR;
    }

    /** Rapport de contraste WCAG entre deux couleurs #RRGGBB (de 1 à 21). */
    public static double contraste(String a, String b) {
        double la = luminance(a);
        double lb = luminance(b);
        return rapport(Math.max(la, lb), Math.min(la, lb));
    }

    private static double rapport(double claire, double sombre) {
        return (claire + 0.05) / (sombre + 0.05);
    }

    /** Luminance relative WCAG 2.x. */
    static double luminance(String couleur) {
        int rgb = Integer.parseInt(couleur.substring(1), 16);
        return 0.2126 * canal((rgb >> 16) & 0xFF) + 0.7152 * canal((rgb >> 8) & 0xFF) + 0.0722 * canal(rgb & 0xFF);
    }

    private static double canal(int valeur) {
        double c = valeur / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
