package com.batulo.service.model;

import java.util.List;

/** Résultat d'un import (CSV ou reprise d'une autre liste) : lignes acceptées, lignes refusées et leur raison. */
public record RapportImportModel(int acceptees, List<LigneRefusee> refusees) {

    /**
     * @param ligne  numéro de ligne dans le fichier (en-tête = 1) ; pour une reprise, rang dans la liste source
     * @param raison COURRIEL_MANQUANT, COURRIEL_INVALIDE, DOUBLON_FICHIER, DEJA_INVITE, LANGUE_INVALIDE,
     *               TEXTE_TROP_LONG, TELEPHONE_INVALIDE ou LIMITE_INVITES
     */
    public record LigneRefusee(int ligne, String courriel, String raison, String message) {
    }
}
