package com.batulo.service.model;

public record AdminDetailResponse(
        UtilisateurResponse admin,
        /** Null seulement si l'entreprise n'a pas d'abonnement (ne devrait pas arriver). */
        AbonnementResponse abonnement,
        long nombreGestionnaires
) {
}
