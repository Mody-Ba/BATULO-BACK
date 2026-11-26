package com.batulo.repository;

/** Solde et totaux de crédits d'une entreprise, lus par une requête scalaire. */
public record InfosCredits(int solde, int totalAchete, int totalUtilise) {
}
