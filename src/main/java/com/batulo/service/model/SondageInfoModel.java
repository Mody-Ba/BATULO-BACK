package com.batulo.service.model;

public record SondageInfoModel(
        String titre,
        String description,
        /** Null : inchangé. */
        Boolean reponseUnique,
        /** Champs de la page d'accueil : null = inchangé, texte vide = effacé. */
        String objetEnquete,
        String texteConfidentialite,
        Boolean consentementRequis,
        String texteConsentement,
        /** Null : inchangé. */
        Boolean reponsesNominatives
) {
}
