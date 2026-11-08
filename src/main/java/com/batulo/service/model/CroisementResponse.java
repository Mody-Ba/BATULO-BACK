package com.batulo.service.model;

import com.batulo.enums.TypeQuestion;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tableau croisé (module 4.3) : une question (lignes : ses modalités) par un critère (colonnes : ses valeurs, plus
 * « Non renseigné » si besoin). Question NUMERIQUE : statistiques par colonne au lieu des lignes ; ECHELLE : les deux.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CroisementResponse(
        Entete question,
        Entete par,
        List<CritereResponse> criteres,
        List<Colonne> colonnes,
        /** CHOIX_UNIQUE, CHOIX_MULTIPLE, OUI_NON, ECHELLE. */
        List<Ligne> lignes,
        /** ECHELLE, NUMERIQUE : nombre, moyenne, médiane, min et max par colonne. */
        List<NumeriqueColonne> numerique
) {
    public record Entete(String cle, String libelle, TypeQuestion type) {
    }

    /** valeur null : répondants qui n'ont pas renseigné le critère. */
    public record Colonne(String valeur, String libelle, long nombreRepondants, boolean petitEffectif) {
    }

    public record Ligne(String valeur, String libelle, long total, List<Cellule> cellules) {
    }

    /** pourcentageLigne : part de la ligne ; pourcentageColonne : part des répondants de la colonne. */
    public record Cellule(String colonne, long nombre, BigDecimal pourcentageLigne, BigDecimal pourcentageColonne,
                          boolean petitEffectif) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NumeriqueColonne(String colonne, long nombre, BigDecimal moyenne, BigDecimal mediane,
                                   BigDecimal min, BigDecimal max, boolean petitEffectif) {
    }
}
