package com.batulo.service.email;

import com.batulo.enums.Langue;

import java.time.Instant;

/**
 * Contenu d'un courriel d'invitation ou de relance.
 *
 * @param langue        langue du message (fr, en, ar)
 * @param prenom        prénom de l'invité ; null : salutation sans nom
 * @param titreSondage  titre dans la langue du message (texte principal en secours)
 * @param entreprise    nom affiché de l'entreprise
 * @param lien          lien personnel complet
 * @param dateFermeture fermeture prévue ; null si aucune
 * @param relance       true : courriel de relance
 */
public record MessageInvitation(Langue langue, String prenom, String titreSondage, String entreprise, String lien,
                                Instant dateFermeture, boolean relance) {

    /** Le lien (secret) n'est jamais écrit dans les logs. */
    @Override
    public String toString() {
        return "MessageInvitation[langue=" + langue + ", relance=" + relance + "]";
    }
}
