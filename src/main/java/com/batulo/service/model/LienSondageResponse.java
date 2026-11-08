package com.batulo.service.model;

/** Lien public d'un sondage : le même pour toutes ses versions. */
public record LienSondageResponse(
        String jeton,
        String url
) {
}
