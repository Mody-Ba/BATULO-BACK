package com.batulo.service.model;

/** Personnalisation de l'entreprise (logo, couleur, nom affiché sur les formulaires). */
public record PersonnalisationResponse(
        /** Nom de l'entreprise (compte). */
        String nomEntreprise,
        /** Nom affiché saisi par l'admin ; null : le nom de l'entreprise est utilisé. */
        String nomAffiche,
        /** Nom réellement affiché aux répondants et dans les rapports. */
        String nomEffectif,
        /** Couleur saisie (#RRGGBB) ; null : couleur Batulo par défaut. */
        String couleurPrincipale,
        /** Couleur réellement utilisée (#RRGGBB). */
        String couleurEffective,
        /** #FFFFFF ou #000000 : texte lisible sur la couleur effective (contraste WCAG). */
        String couleurTexte,
        /** Null : pas de logo. */
        LogoInfoResponse logo
) {
}
