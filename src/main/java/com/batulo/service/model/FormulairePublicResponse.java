package com.batulo.service.model;

import com.batulo.enums.Langue;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Formulaire affiché au répondant (RE-01, RE-02) : uniquement ce qu'il faut pour répondre, aucune donnée interne
 * (ni id de sondage, ni entreprise, ni créateur, ni version).
 */
public record FormulairePublicResponse(
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
        ApparenceResponse apparence,
        /** Langue des textes renvoyés : celle demandée (?langue=) si elle est proposée, sinon la langue principale. */
        Langue langue,
        Langue languePrincipale,
        /** Langues proposées au répondant, la langue principale d'abord. */
        List<Langue> languesDisponibles,
        List<SectionPubliqueResponse> sections,
        /**
         * Seulement si reponseUnique : true si l'appareil (en-tête X-Jeton-Repondant) a déjà répondu à l'une des
         * versions ; false sans jeton ou avec un jeton mal formé. Absent sinon.
         */
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "Seulement si reponseUnique : l'appareil (X-Jeton-Repondant) a déjà répondu")
        Boolean dejaRepondu
) {
}
