package com.batulo.service.model;

import java.util.List;
import java.math.BigDecimal;
import com.batulo.enums.TypePaiement;
/**
 * Tableau de bord du super admin. Les entreprises sont comptées par statut d'abonnement affiché (recalculé à
 * partir des dates) ; le total compte toutes les entreprises.
 */
public record TableauDeBordSuperAdminModel(
        Entreprises entreprises,
        long demandesAugmentationEnAttente,
        Revenus revenus
) {

    /** Paiements réussis, par type et par devise ; sondagesVendus : total des quantités achetées. */
    public record Revenus(List<LigneRevenus> lignes, long sondagesVendus) {
    }

    public record LigneRevenus(TypePaiement type, String devise, BigDecimal montant, long nombrePaiements) {
    }

    public record Entreprises(long total, long actif, long expireBientot, long expire, long enAttentePaiement,
                              long desactive) {
    }
}
