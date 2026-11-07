package com.batulo.service.model;

/** Ligne de la liste des abonnements du super admin : l'abonnement et l'admin de l'entreprise. */
public record AbonnementAdminModel(
        AbonnementModel abonnement,
        Long adminId,
        String adminPrenom,
        String adminNom,
        String adminCourriel
) {
}
