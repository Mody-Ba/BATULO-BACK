package com.batulo.service.model;

import java.time.Instant;

/** Logo d'une entreprise : URL publique (versionnée) et description, sans le contenu. */
public record LogoInfoModel(String url, String typeMime, int taille, int largeur, int hauteur, Instant dateModification) {
}
