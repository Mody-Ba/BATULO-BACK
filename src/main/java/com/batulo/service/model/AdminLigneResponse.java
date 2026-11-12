package com.batulo.service.model;

import com.batulo.enums.Formule;
import com.batulo.enums.StatutAbonnement;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record AdminLigneResponse(
        Entreprise entreprise,
        Admin admin,
        @Schema(description = "Null si l'entreprise n'a pas d'abonnement")
        Abonnement abonnement,
        long nombreGestionnaires
) {

    public record Entreprise(Long id, String nom, boolean actif) {
    }

    public record Admin(
            Long id,
            String prenom,
            String nom,
            String courriel,
            boolean actif,
            @Schema(description = "true : le mot de passe a été généré par le serveur et doit être changé")
            boolean motDePasseTemporaire,
            @Schema(description = "Fin de validité du mot de passe temporaire ; null s'il n'y en a pas")
            Instant motDePasseTemporaireExpireLe,
            Instant dateCreation) {
    }

    public record Abonnement(
            @Schema(description = "Null tant qu'aucune formule n'a été payée")
            Formule formule,
            @Schema(description = "Statut affiché, recalculé à partir des dates")
            StatutAbonnement statut,
            Instant dateExpiration,
            @Schema(description = "Jours avant l'expiration (arrondi au jour supérieur), 0 si expiré, null si jamais payé")
            Long joursRestants) {
    }
}
