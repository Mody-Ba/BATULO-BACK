package com.batulo.service.model;

import java.util.List;

/** Libellés affichés d'une matrice dans une langue, au même rang que ses lignes et colonnes (valeurs stables). */
public record LibellesMatriceModel(List<String> lignes, List<String> colonnes) {
}
