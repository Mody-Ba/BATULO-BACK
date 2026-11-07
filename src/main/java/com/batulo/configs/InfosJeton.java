package com.batulo.configs;

import com.batulo.enums.Role;

/** Contenu vérifié d'un jeton d'accès : l'identifiant du compte et son rôle au moment de la connexion. */
public record InfosJeton(
        Long utilisateurId,
        Role role
) {
}
