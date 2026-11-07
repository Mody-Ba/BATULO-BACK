package com.batulo.service.model;

/** Un admin, l'abonnement de son entreprise et le nombre de ses gestionnaires (SA-03). */
public record AdminDetailModel(
        UtilisateurModel admin,
        AbonnementModel abonnement,
        long nombreGestionnaires
) {
}
