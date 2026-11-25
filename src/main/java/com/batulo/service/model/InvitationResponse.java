package com.batulo.service.model;

import com.batulo.enums.Langue;
import com.batulo.enums.StatutInvitation;

import java.time.Instant;

/** Un invité et le suivi de son invitation. Le lien personnel n'est jamais renvoyé (il part seulement par courriel). */
public record InvitationResponse(
        Long id,
        String prenom,
        String nom,
        String courriel,
        String telephone,
        /** Langue préférée ; null : langue principale du sondage. */
        Langue langue,
        StatutInvitation statut,
        /** Courriel demandé, pas encore parti (envoi en arrière-plan). */
        boolean envoiEnCours,
        Instant dateCreation,
        Instant premierEnvoiLe,
        Instant dernierEnvoiLe,
        Instant derniereRelanceLe,
        int nombreEnvois,
        /** Dernier envoi en échec ; null si le dernier envoi a réussi. */
        Instant echecEnvoiLe,
        /** Première ouverture du formulaire avec le lien personnel. */
        Instant ouverteLe,
        /** Réponse : arrondie au jour (minuit, fuseau de Batulo) si les réponses sont anonymes. */
        Instant reponduLe,
        Instant revoqueeLe
) {
}
