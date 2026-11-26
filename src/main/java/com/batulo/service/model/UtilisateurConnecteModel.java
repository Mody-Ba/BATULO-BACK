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
        StatutAbonnement statutAbonnement,
        /**
         * L'entreprise a déjà acheté des sondages : accès complet malgré un abonnement EN_ATTENTE_PAIEMENT ou EXPIRE.
         * Calculé seulement dans ces deux cas (false sinon).
         */
        boolean compteCredits
) {
}
