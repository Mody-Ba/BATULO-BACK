package com.batulo.service.model;

/**
 * Sondages achetés par une entreprise : solde disponible (sans date limite), total acheté et total utilisé. Une
 * entreprise qui a déjà acheté (totalAchete > 0) a un « compte de crédits » : accès complet sans abonnement actif.
 */
public record CreditsModel(
        int solde,
        int totalAchete,
        int totalUtilise
) {
    public static final CreditsModel AUCUN = new CreditsModel(0, 0, 0);

    public boolean compteCredits() {
        return totalAchete > 0;
    }
}
