package com.batulo.service.model;

import com.batulo.enums.StatutAbonnement;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProfilResponse(
        UtilisateurResponse utilisateur,
        StatutAbonnement statutAbonnement,
        @Schema(description = "Sondages achetés de l'entreprise ; null pour le super admin. compteCredits vrai : "
                + "accès complet même si statutAbonnement est EN_ATTENTE_PAIEMENT ou EXPIRE")
        CreditsResponse credits
) {
}
