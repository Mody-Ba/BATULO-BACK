package com.batulo.service.model;

import com.batulo.enums.TypeNotification;

import java.time.Instant;

public record NotificationModel(
        Long id,
        TypeNotification type,
        String titre,
        String message,
        boolean lue,
        Instant dateCreation,
        /**
         * Élément concerné, selon le type : abonnement (inscription, paiement, expiration), sondage (lignée,
         * assignation), message (nouveau message), envoi (statistiques reçues), demande (demandes d'abonnement).
         * Null pour les anciennes notifications.
         */
        Long referenceId,
        /** Conversation à ouvrir (nouveau message, statistiques reçues, demandes d'abonnement) ; null sinon. */
        Long conversationId
) {
}
