package com.batulo.service.model;

import java.util.List;
import java.math.BigDecimal;
import com.batulo.enums.TypePaiement;
import io.swagger.v3.oas.annotations.media.Schema;

public record TableauDeBordSuperAdminResponse(
        Entreprises entreprises,
        @Schema(description = "Demandes d'augmentation d'abonnement EN_ATTENTE")
        long demandesAugmentationEnAttente,
        Revenus revenus
) {

    @Schema(description = "Revenus : paiements réussis (abonnements et achats de sondages), par type et par devise")
    public record Revenus(
            @Schema(description = "Une ligne par type (ABONNEMENT, ACHAT_SONDAGES) et devise (CAD, MRU) encaissés")
            List<LigneRevenus> lignes,
            @Schema(description = "Total des sondages vendus à l'unité")
            long sondagesVendus) {
    }

    public record LigneRevenus(TypePaiement type, String devise, BigDecimal montant, long nombrePaiements) {
    }

    @Schema(description = "Entreprises par statut d'abonnement affiché (recalculé à partir des dates)")
    public record Entreprises(long total, long actif, long expireBientot, long expire, long enAttentePaiement,
                              long desactive) {
    }
}
