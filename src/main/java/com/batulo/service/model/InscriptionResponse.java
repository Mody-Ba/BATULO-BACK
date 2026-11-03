package com.batulo.service.model;

import com.batulo.enums.StatutAbonnement;

public record InscriptionResponse(
        String message,
        UtilisateurResponse utilisateur,
        Long abonnementId,
        /** EN_ATTENTE_PAIEMENT tant que l'admin n'a pas payé (RG-08). */
        StatutAbonnement statutAbonnement
) {
}
