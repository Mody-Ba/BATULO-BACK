package com.batulo.service.model;

import com.batulo.enums.CoteConversation;
import com.batulo.enums.TypeMessage;

import java.time.Instant;

/** Message d'une conversation. recu : envoyé par l'interlocuteur ; luLe : lu par son destinataire. */
public record MessageInterneModel(
        Long id,
        Long conversationId,
        TypeMessage type,
        /** Envoi de statistiques (STATISTIQUES) ou demande d'abonnement (DEMANDE_ABONNEMENT, REPONSE_DEMANDE). */
        Long referenceId,
        String contenu,
        Long expediteurId,
        String expediteurNom,
        CoteConversation cote,
        boolean recu,
        Instant dateEnvoi,
        Instant luLe
) {
}
