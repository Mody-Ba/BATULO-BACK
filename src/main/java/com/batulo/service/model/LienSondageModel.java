package com.batulo.service.model;

/** Lien public d'un sondage : le même pour toutes ses versions. */
public record LienSondageModel(
        String jeton,
        String url
) {
}
