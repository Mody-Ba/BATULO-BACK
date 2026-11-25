package com.batulo.service.model;

import java.math.BigDecimal;

/** Compteurs du suivi des invitations d'un sondage (toutes versions). */
public record ResumeInvitationsModel(
        long total,
        long aInviter,
        long invitees,
        long ouvertes,
        long repondues,
        long revoquees,
        long enEchec,
        /** Invitations envoyées au moins une fois avec succès, hors révoquées. */
        long envoyees,
        /** repondues / envoyees, en % arrondi à 0,1 ; 0 si aucune n'a été envoyée. */
        BigDecimal tauxReponse
) {
}
