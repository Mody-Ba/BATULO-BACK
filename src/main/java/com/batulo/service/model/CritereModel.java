package com.batulo.service.model;

import com.batulo.enums.TypeQuestion;

import java.util.List;

/**
 * Critère d'analyse (segmentation) et ses valeurs : options (CHOIX_UNIQUE), true/false (OUI_NON), tranches t1..tN
 * (NUMERIQUE), mois « AAAA-MM » ou années « AAAA » présents dans les réponses (DATE). Un filtre s'écrit
 * « critere=cle:valeur ».
 */
public record CritereModel(
        String cle,
        Long questionId,
        String libelle,
        TypeQuestion type,
        List<ValeurCritere> valeurs
) {
    public record ValeurCritere(String valeur, String libelle) {
    }
}
