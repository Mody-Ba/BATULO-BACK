package com.batulo.service.model;

/** Compte qui a créé le sondage ; null s'il a été supprimé. */
public record AuteurModel(
        Long id,
        String prenom,
        String nom
) {
}
