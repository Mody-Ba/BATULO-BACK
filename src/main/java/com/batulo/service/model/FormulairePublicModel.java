package com.batulo.service.model;

import java.time.Instant;
import java.util.List;

/**
 * Formulaire affiché au répondant (RE-01, RE-02) : uniquement ce qu'il faut pour répondre, aucune donnée interne
 * (ni id de sondage, ni entreprise, ni créateur, ni version).
 */
public record FormulairePublicModel(
        String titre,
        String description,
        /** true : une seule réponse par appareil ; le frontend envoie alors son jetonRepondant. */
        boolean reponseUnique,
        /** Fermeture prévue ; null si aucune. */
        Instant dateFermeture,
        /** Page d'accueil : objet de l'enquête ; null si aucun. */
        String objetEnquete,
        /** Page d'accueil : texte sur la confidentialité des réponses ; null si aucun. */
        String texteConfidentialite,
        /** true : le répondant doit cocher la case de consentement pour envoyer ses réponses. */
        boolean consentementRequis,
        /** Texte de la case de consentement (renseigné si consentementRequis). */
        String texteConsentement,
        /** Apparence à l'image de l'entreprise (nom affiché, logo, couleurs) ; rien d'autre sur l'entreprise. */
        ApparenceModel apparence,
        List<SectionModel> sections,
        /** reponseUnique : cet appareil (jeton X-Jeton-Repondant) a déjà répondu sur la lignée ; null sinon. */
        Boolean dejaRepondu
) {
}
