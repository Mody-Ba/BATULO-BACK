package com.batulo.service.model;

public record SondageInfoModel(
        String titre,
        String description,
        /** Null : inchangé. */
        Boolean reponseUnique
) {
}
