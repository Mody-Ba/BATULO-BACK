package com.batulo.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecture d'un petit fichier CSV (import des invités) : UTF-8 avec ou sans BOM, séparateur « , » ou « ; » détecté sur
 * la ligne d'en-tête, champs entre guillemets (séparateurs, guillemets doublés et retours à la ligne compris).
 */
final class LecteurCsv {

    /** Un enregistrement et le numéro de la ligne du fichier où il commence (1 = en-tête). */
    record Enregistrement(int ligne, List<String> champs) {
    }

    private LecteurCsv() {
    }

    static List<Enregistrement> lire(String texte) {
        if (texte.startsWith("﻿")) {
            texte = texte.substring(1);
        }
        char separateur = separateur(texte);
        List<Enregistrement> resultat = new ArrayList<>();
        List<String> champs = new ArrayList<>();
        StringBuilder champ = new StringBuilder();
        boolean guillemets = false;
        int ligne = 1;
        int debut = 1;
        for (int i = 0; i < texte.length(); i++) {
            char c = texte.charAt(i);
            if (guillemets) {
                if (c == '"') {
                    if (i + 1 < texte.length() && texte.charAt(i + 1) == '"') {
                        champ.append('"');
                        i++;
                    } else {
                        guillemets = false;
                    }
                } else {
                    if (c == '\n') {
                        ligne++;
                    }
                    champ.append(c);
                }
            } else if (c == '"' && champ.isEmpty()) {
                guillemets = true;
            } else if (c == separateur) {
                champs.add(champ.toString());
                champ.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < texte.length() && texte.charAt(i + 1) == '\n') {
                    i++;
                }
                champs.add(champ.toString());
                champ.setLength(0);
                resultat.add(new Enregistrement(debut, List.copyOf(champs)));
                champs.clear();
                ligne++;
                debut = ligne;
            } else {
                champ.append(c);
            }
        }
        if (!champ.isEmpty() || !champs.isEmpty()) {
            champs.add(champ.toString());
            resultat.add(new Enregistrement(debut, List.copyOf(champs)));
        }
        // Lignes vides (fin de fichier, lignes blanches) ignorées.
        resultat.removeIf(e -> e.champs().stream().allMatch(String::isBlank));
        return resultat;
    }

    /** « ; » s'il y en a plus que de « , » sur la première ligne, sinon « , ». */
    private static char separateur(String texte) {
        int fin = texte.indexOf('\n');
        String entete = fin < 0 ? texte : texte.substring(0, fin);
        long pointsVirgules = entete.chars().filter(c -> c == ';').count();
        long virgules = entete.chars().filter(c -> c == ',').count();
        return pointsVirgules > virgules ? ';' : ',';
    }
}
