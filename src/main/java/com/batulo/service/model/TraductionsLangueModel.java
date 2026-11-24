package com.batulo.service.model;

import com.batulo.enums.Langue;

import java.util.List;

/** Textes d'un sondage pour une langue, côte à côte : texte principal et traduction (null si absente). */
public record TraductionsLangueModel(
        Long sondageId,
        Langue langue,
        Langue languePrincipale,
        int traduits,
        int total,
        boolean complet,
        List<Texte> textes
) {
    public record Texte(String cle, String element, String champ, Long sectionId, Long questionId, Long optionId,
                        String source, String traduction, int longueurMax) {
    }
}
