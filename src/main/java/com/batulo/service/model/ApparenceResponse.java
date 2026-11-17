package com.batulo.service.model;

/**
 * Apparence du formulaire à l'image de l'entreprise. Seules ces quatre informations sont exposées au répondant.
 */
public record ApparenceResponse(
        /** Nom affiché (ou nom de l'entreprise). */
        String nomAffiche,
        /** URL absolue du logo ; null si l'entreprise n'en a pas. */
        String logoUrl,
        /** #RRGGBB (couleur de l'entreprise ou couleur Batulo par défaut). */
        String couleurPrincipale,
        /** #FFFFFF ou #000000 : texte lisible sur la couleur principale (contraste WCAG). */
        String couleurTexte
) {
}
