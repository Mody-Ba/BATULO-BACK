package com.batulo.service.model;

import com.batulo.enums.StatutAbonnement;

public record ProfilResponse(
        UtilisateurResponse utilisateur,
        StatutAbonnement statutAbonnement
) {
}
