package com.batulo.service.model;

import com.batulo.enums.StatutAbonnement;

/** Profil de l'utilisateur connecté et statut de l'abonnement de son entreprise (null pour le super admin). */
public record ProfilModel(
        UtilisateurModel utilisateur,
        StatutAbonnement statutAbonnement
) {
}
