package com.batulo.repository;

import java.time.Instant;

/** Description d'un logo, sans son contenu (lecture légère pour les formulaires et les réglages). */
public record InfosLogo(Long entrepriseId, String jeton, String typeMime, int taille, int largeur, int hauteur,
                        Instant dateModification) {
}
