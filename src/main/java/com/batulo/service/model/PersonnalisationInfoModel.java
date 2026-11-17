package com.batulo.service.model;

/** Réglages envoyés par l'admin : null = inchangé, texte vide = effacé. */
public record PersonnalisationInfoModel(String nomAffiche, String couleurPrincipale) {
}
