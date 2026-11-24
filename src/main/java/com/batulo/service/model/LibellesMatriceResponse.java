package com.batulo.service.model;

import java.util.List;

/** Libellés affichés d'une matrice dans la langue servie, au même rang que configuration.lignes / colonnes. */
public record LibellesMatriceResponse(List<String> lignes, List<String> colonnes) {
}
