package com.batulo.service.model;

import java.time.Instant;

public record MotDePasseTemporaireResponse(
        String message,
        Long adminId,
        String courriel,
        Instant expireLe
) {
}
