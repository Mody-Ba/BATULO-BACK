package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
import com.batulo.enums.OffreInscription;
import com.batulo.enums.StatutAbonnement;

public record InscriptionResponse(
        String message,
        UtilisateurResponse utilisateur,
        Long abonnementId,
        /** EN_ATTENTE_PAIEMENT tant que l'admin n'a pas payé (RG-08). */
        StatutAbonnement statutAbonnement,
        @Schema(description = "Offre choisie (null si aucune) : le frontend enchaîne sur le paiement correspondant "
                + "(carte ou mobile)")
        OffreInscription offre,
        @Schema(description = "Nombre de sondages choisi avec ACHAT_SONDAGES ; null sinon")
        Integer nombreSondages
) {
}
