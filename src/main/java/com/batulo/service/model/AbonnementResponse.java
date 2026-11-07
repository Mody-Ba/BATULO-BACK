package com.batulo.service.model;

import com.batulo.enums.Formule;
import com.batulo.enums.StatutAbonnement;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

public record AbonnementResponse(
        Long id,
        Long entrepriseId,
        String entreprise,
        @Schema(description = "Null tant qu'aucune formule n'a été payée")
        Formule formule,
        StatutAbonnement statut,
        Instant dateDebut,
        Instant dateExpiration,
        @Schema(description = "Jours avant l'expiration (arrondi au jour supérieur), 0 si expiré, null si jamais payé")
        Long joursRestants,
        @Schema(description = "Montant du dernier paiement")
        BigDecimal montant,
        String devise
) {
}
