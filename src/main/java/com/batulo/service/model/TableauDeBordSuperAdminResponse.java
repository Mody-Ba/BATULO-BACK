package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record TableauDeBordSuperAdminResponse(
        Entreprises entreprises,
        @Schema(description = "Demandes d'augmentation d'abonnement EN_ATTENTE")
        long demandesAugmentationEnAttente
) {

    @Schema(description = "Entreprises par statut d'abonnement affiché (recalculé à partir des dates)")
    public record Entreprises(long total, long actif, long expireBientot, long expire, long enAttentePaiement,
                              long desactive) {
    }
}
