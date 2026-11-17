package com.batulo.service.model;

public record AdminDetailResponse(
        UtilisateurResponse admin,
        /** Null seulement si l'entreprise n'a pas d'abonnement (ne devrait pas arriver). */
        AbonnementResponse abonnement,
        long nombreGestionnaires,
        /** Apparence de l'entreprise (logo, couleur, nom affiché), en lecture seule pour le super admin. */
        PersonnalisationResponse personnalisation
) {
}
