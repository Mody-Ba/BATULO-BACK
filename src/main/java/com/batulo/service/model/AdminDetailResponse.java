package com.batulo.service.model;

import io.swagger.v3.oas.annotations.media.Schema;
public record AdminDetailResponse(
        UtilisateurResponse admin,
        /** Null seulement si l'entreprise n'a pas d'abonnement (ne devrait pas arriver). */
        AbonnementResponse abonnement,
        long nombreGestionnaires,
        /** Apparence de l'entreprise (logo, couleur, nom affiché), en lecture seule pour le super admin. */
        PersonnalisationResponse personnalisation,
        @Schema(description = "Sondages achetés de l'entreprise : solde, total acheté, total utilisé (lecture seule) ; "
                + "historique : GET /api/super-admin/admins/{id}/achats-sondages")
        CreditsResponse credits
) {
}
