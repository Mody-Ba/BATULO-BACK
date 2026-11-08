package com.batulo.service.model;

/** Compte qui a créé le sondage ; null s'il a été supprimé. */
public record AuteurResponse(
        Long id,
        String prenom,
        String nom
) {
}
