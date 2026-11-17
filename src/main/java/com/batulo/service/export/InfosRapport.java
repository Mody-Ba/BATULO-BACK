package com.batulo.service.export;

import java.util.List;

/**
 * En-tête d'un rapport (page de titre du PDF, feuille « Résumé » d'Excel) : déjà mis en forme pour l'affichage.
 *
 * @param perimetre « Version 2 » ou « Toutes les versions (1, 2, 3) »
 * @param periode   « Du 2026-07-01 au 2026-07-31 » ou « Toutes les dates »
 * @param criteres  critères appliqués, ex. « Région : Kayes, Kaolack » ; vide : tous les répondants
 * @param entreprise nom affiché de l'entreprise (ou son nom)
 * @param logo       logo de l'entreprise (PNG ou JPEG) pour l'en-tête du PDF ; null s'il n'y en a pas
 * @param personnalise l'entreprise a un nom affiché ou un logo : l'en-tête des pages porte son nom au lieu de « Batulo »
 */
public record InfosRapport(
        String titre,
        String entreprise,
        String perimetre,
        String periode,
        List<String> criteres,
        String genereLe,
        int effectifMinimum,
        byte[] logo,
        boolean personnalise
) {
}
