package com.batulo.service.model;

/**
 * Tableau de bord du super admin. Les entreprises sont comptées par statut d'abonnement affiché (recalculé à
 * partir des dates) ; le total compte toutes les entreprises.
 */
public record TableauDeBordSuperAdminModel(
        Entreprises entreprises,
        long demandesAugmentationEnAttente
) {

    public record Entreprises(long total, long actif, long expireBientot, long expire, long enAttentePaiement,
                              long desactive) {
    }
}
