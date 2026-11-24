package com.batulo.service.model;

import com.batulo.enums.Langue;
import com.batulo.enums.StatutSondage;

import java.util.List;

/**
 * Prévisualisation : le formulaire tel que le verra le répondant, sans les informations internes
 * (créateur, entreprise, dates de création et de modification).
 */
public record ApercuModel(
        Long id,
        String titre,
        String description,
        int version,
        StatutSondage statut,
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
        /** Langue des textes renvoyés : celle demandée (?langue=) si elle est proposée, sinon la langue principale. */
        Langue langue,
        Langue languePrincipale,
        /** Langues proposées au répondant, la langue principale d'abord. */
        List<Langue> languesDisponibles,
        List<SectionModel> sections
) {
}
