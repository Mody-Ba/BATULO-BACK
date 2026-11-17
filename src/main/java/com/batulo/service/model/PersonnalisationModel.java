package com.batulo.service.model;

/**
 * Personnalisation d'une entreprise : réglages saisis par l'admin (null si non renseignés) et valeurs effectives
 * (défauts compris) telles que les voient les répondants.
 */
public record PersonnalisationModel(
        String nomEntreprise,
        String nomAffiche,
        String nomEffectif,
        String couleurPrincipale,
        String couleurEffective,
        String couleurTexte,
        LogoInfoModel logo
) {
}
