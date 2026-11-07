package com.batulo.service.model;

import java.time.Instant;

/** Un nouveau mot de passe temporaire a été généré ; il n'est jamais renvoyé par l'API. */
public record MotDePasseTemporaireModel(
        Long adminId,
        String courriel,
        Instant expireLe
) {
}
