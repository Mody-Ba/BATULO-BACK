package com.batulo.service.model;

import com.batulo.enums.Role;

import java.time.Instant;

/**
 * Couche interne neutre : ni le contrat API (UtilisateurResponse), ni l'entité JPA.
 * Ne porte volontairement pas le mot de passe (ni en clair ni chiffré).
 */
public record UtilisateurModel(
        Long id,
        String prenom,
        String nom,
        String courriel,
        Role role,
        Long entrepriseId,
        String entrepriseNom,
        boolean actif,
        boolean motDePasseTemporaire,
        boolean peutCreerSondages,
        Instant dateCreation
) {
}
