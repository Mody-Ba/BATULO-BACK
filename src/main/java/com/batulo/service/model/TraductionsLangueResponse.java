package com.batulo.service.model;

import com.batulo.enums.Langue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Écran côte à côte d'une langue : chaque texte traduisible avec sa version principale et sa traduction. */
public record TraductionsLangueResponse(
        Long sondageId,
        Langue langue,
        Langue languePrincipale,
        /** Textes traduits (traduction non vide). */
        int traduits,
        /** Textes traduisibles (textes non vides de la langue principale). */
        int total,
        boolean complet,
        /** Dans l'ordre du formulaire. */
        List<Texte> textes
) {
    @Schema(name = "TexteTraduction")
    public record Texte(
            /** Clé à renvoyer à l'enregistrement, ex. sondage.titre, section.3.titre, question.12.ligne.2, option.40.libelle. */
            String cle,
            /** SONDAGE, SECTION, QUESTION, OPTION, LIGNE ou COLONNE. */
            String element,
            /** titre, description, objetEnquete, texteConfidentialite, texteConsentement, libelle, aide, libelleMin, libelleMax, lignes, colonnes. */
            String champ,
            Long sectionId,
            Long questionId,
            Long optionId,
            /** Texte dans la langue principale. */
            String source,
            /** Traduction ; null si absente. */
            String traduction,
            int longueurMax
    ) {
    }
}
