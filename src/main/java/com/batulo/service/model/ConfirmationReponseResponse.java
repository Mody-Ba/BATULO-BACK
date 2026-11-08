package com.batulo.service.model;

import java.time.Instant;

public record ConfirmationReponseResponse(
        String message,
        Instant dateSoumission
) {
}
