package com.batulo.service.model;

import com.batulo.enums.StatutAbonnement;

/** Session ouverte : jeton d'accès court, jeton de rafraîchissement et état du compte. */
public record ConnexionModel(
        String jeton,
        long expireDansSecondes,
        String jetonRafraichissement,
        UtilisateurModel utilisateur,
        /** Null pour le super admin. */
        StatutAbonnement statutAbonnement
) {
    @Override
    public String toString() {
        return "ConnexionModel[jeton=****, jetonRafraichissement=****, utilisateur=" + utilisateur.id() + "]";
    }
}
