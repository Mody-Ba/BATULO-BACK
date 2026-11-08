package com.batulo.service.export;

import java.util.List;

/**
 * En-tête d'un rapport (page de titre du PDF, feuille « Résumé » d'Excel) : déjà mis en forme pour l'affichage.
 *
 * @param perimetre « Version 2 » ou « Toutes les versions (1, 2, 3) »
 * @param periode   « Du 2026-07-01 au 2026-07-31 » ou « Toutes les dates »
 * @param criteres  critères appliqués, ex. « Région : Kayes, Kaolack » ; vide : tous les répondants
 */
public record InfosRapport(
        String titre,
        String entreprise,
        String perimetre,
        String periode,
        List<String> criteres,
        String genereLe,
        int effectifMinimum
) {
}
