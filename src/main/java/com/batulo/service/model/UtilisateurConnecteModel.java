package com.batulo.service.model;

import com.batulo.enums.Role;
import com.batulo.enums.StatutAbonnement;

/** État d'un compte relu à chaque requête authentifiée (filtre JWT). */
public record UtilisateurConnecteModel(
        Long id,
        String courriel,
        Role role,
        Long entrepriseId,
        boolean actif,
        boolean motDePasseTemporaire,
        /** Null pour le super admin. */
        StatutAbonnement statutAbonnement
) {
}
