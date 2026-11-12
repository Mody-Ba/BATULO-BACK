package com.batulo.service.model;

/** Gestionnaire assigné à la lignée d'un sondage (liste des sondages, visible par l'admin seulement). */
public record GestionnaireAssigneModel(
        Long id,
        String prenom,
        String nom,
        boolean actif
) {
}
