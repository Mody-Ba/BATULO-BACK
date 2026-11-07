package com.batulo.configs.security;

import com.batulo.enums.Role;
import com.batulo.enums.StatutAbonnement;

/**
 * Utilisateur de la requête, placé dans le SecurityContext par {@link AuthTokenFilter}.
 * Relu en base à chaque requête : un compte désactivé ou un abonnement qui change est pris en compte
 * immédiatement, sans attendre l'expiration du jeton.
 */
public record UtilisateurConnecte(
        Long id,
        String courriel,
        Role role,
        /** Null pour le super admin. */
        Long entrepriseId,
        boolean motDePasseTemporaire,
        /** Statut effectif de l'abonnement de l'entreprise ; null pour le super admin. */
        StatutAbonnement statutAbonnement
) {
}
