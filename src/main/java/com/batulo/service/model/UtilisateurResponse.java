package com.batulo.service.model;

import com.batulo.enums.Role;

import java.time.Instant;

public record UtilisateurResponse(
        Long id,
        String prenom,
        String nom,
        String courriel,
        Role role,
        /** Null pour le super admin. */
        Long entrepriseId,
        String entreprise,
        boolean actif,
        /** true : le mot de passe a été généré par le serveur et doit être changé. */
        boolean motDePasseTemporaire,
        boolean peutCreerSondages,
        Instant dateCreation
) {
}
