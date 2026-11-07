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
        /** Fin de validité du mot de passe temporaire (24 h) ; null s'il n'y en a pas. */
        Instant motDePasseTemporaireExpireLe,
        boolean peutCreerSondages,
        Instant dateCreation
) {
}
