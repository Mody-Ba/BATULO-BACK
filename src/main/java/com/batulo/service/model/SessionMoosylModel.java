package com.batulo.service.model;

import java.time.Instant;

/** Session de paiement créée chez Moosyl : la page de paiement où rediriger l'admin. */
public record SessionMoosylModel(
        String id,
        String checkoutUrl,
        /** Fin de validité de la session ; null si Moosyl ne la donne pas. */
        Instant expireLe
) {
}
