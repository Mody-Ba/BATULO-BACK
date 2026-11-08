package com.batulo.service.model;

import java.time.Instant;

public record DatesSondageModel(
        Instant datePublication,
        Instant dateFermeture
) {
}
