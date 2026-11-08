package com.batulo.service.model;

public record CreationSondageModel(
        String titre,
        String description,
        /** Null : sondage vide. */
        Long sondageSourceId
) {
}
