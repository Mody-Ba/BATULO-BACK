package com.batulo.service.model;

import com.batulo.enums.Role;
import com.batulo.enums.StatutAbonnement;
import io.swagger.v3.oas.annotations.media.Schema;

public record ConnexionResponse(
        @Schema(description = "Jeton d'accès à coller dans « Authorize » (en-tête Authorization: Bearer ...)")
        String jeton,
        @Schema(example = "Bearer")
        String typeJeton,
        @Schema(description = "Durée de validité du jeton d'accès, en secondes", example = "900")
        long expireDans,
        @Schema(description = "À envoyer à /api/auth/rafraichir quand le jeton d'accès expire (usage unique)")
        String jetonRafraichissement,
        Role role,
        @Schema(description = "Statut de l'abonnement de l'entreprise ; null pour le super admin")
        StatutAbonnement statutAbonnement,
        @Schema(description = "true : seul /api/auth/** est permis tant que le mot de passe n'est pas changé")
        boolean motDePasseTemporaire,
        UtilisateurResponse utilisateur
) {
    @Override
    public String toString() {
        return "ConnexionResponse[jeton=****, jetonRafraichissement=****, role=" + role + "]";
    }
}
