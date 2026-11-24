package com.batulo.service;

import com.batulo.entity.Sondage;
import com.batulo.enums.Langue;
import com.batulo.exception.RegleGestionException;
import com.batulo.exception.TexteManquant;
import com.batulo.exception.TraductionsIncompletesException;

import java.util.ArrayList;
import java.util.List;

/** Lecture des codes de langue reçus par l'API, et contrôle des traductions à la publication. */
public final class Langues {

    public static final String CODE_INVALIDE = "LANGUE_INVALIDE";

    private Langues() {
    }

    /** « fr », « en » ou « ar » (casse indifférente) ; null ou vide : null ; autre : 400 LANGUE_INVALIDE. */
    public static Langue lire(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return Langue.parCode(code).orElseThrow(() -> new RegleGestionException(CODE_INVALIDE,
                "Langue inconnue : « " + code.trim() + " » (proposées : fr, en, ar)."));
    }

    /** Liste de codes (null : vide) ; chaque code doit être valide. */
    public static List<Langue> lire(List<String> codes) {
        List<Langue> langues = new ArrayList<>();
        if (codes != null) {
            for (String code : codes) {
                Langue langue = lire(code);
                if (langue == null) {
                    throw new RegleGestionException(CODE_INVALIDE, "Code de langue vide.");
                }
                langues.add(langue);
            }
        }
        return langues;
    }

    /** Textes non traduits dans chaque langue supplémentaire, dans l'ordre du formulaire. */
    static List<TexteManquant> manquants(Sondage sondage) {
        List<TexteManquant> manquants = new ArrayList<>();
        List<TextesTraduisibles.Texte> textes = TextesTraduisibles.inventaire(sondage);
        for (Langue langue : sondage.getLanguesSupplementaires()) {
            for (TextesTraduisibles.Texte texte : textes) {
                if (texte.traduction(langue) == null) {
                    manquants.add(new TexteManquant(langue, texte.cle(), texte.source()));
                }
            }
        }
        return manquants;
    }

    /** Publication : toutes les langues supplémentaires doivent être complètes, sinon 400 TRADUCTIONS_INCOMPLETES. */
    static void verifierCompletes(Sondage sondage) {
        List<TexteManquant> manquants = manquants(sondage);
        if (!manquants.isEmpty()) {
            List<String> langues = manquants.stream().map(m -> m.langue().getCode()).distinct().toList();
            throw new TraductionsIncompletesException("Traductions incomplètes (" + String.join(", ", langues)
                    + ") : " + manquants.size() + " texte(s) à traduire avant de publier.", manquants);
        }
    }
}
