package com.batulo.service.model;

import java.time.Instant;

public record LogoInfoResponse(
        /** URL publique du logo ; elle change à chaque remplacement (cache d'un an possible). */
        String url,
        /** image/png ou image/jpeg. */
        String typeMime,
        /** En octets. */
        int taille,
        int largeur,
        int hauteur,
        Instant dateModification
) {
}
