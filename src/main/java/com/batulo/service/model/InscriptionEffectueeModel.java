package com.batulo.service.model;

import com.batulo.enums.StatutAbonnement;

/** Résultat d'une inscription : le compte admin créé et le statut de son abonnement. */
public record InscriptionEffectueeModel(
        UtilisateurModel utilisateur,
        Long abonnementId,
        StatutAbonnement statutAbonnement
) {
}
