package com.batulo.service.model;

import com.batulo.enums.Formule;
import com.batulo.enums.StatutPaiement;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;

public record StatutPaiementMobileResponse(
        String reference,
        @Schema(description = "EN_ATTENTE : pas encore confirmé par Moosyl ; REUSSI : abonnement activé ou prolongé ; "
                + "ECHOUE : refusé, annulé ou session expirée (relancer un paiement)")
        StatutPaiement statut,
        Formule formule,
        BigDecimal montant,
        String devise,
        Instant dateCreation,
        @Schema(description = "Null tant que le paiement n'a pas réussi")
        Instant datePaiement,
        AbonnementResponse abonnement
) {
}
