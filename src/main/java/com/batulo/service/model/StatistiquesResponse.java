package com.batulo.service.model;

import com.batulo.enums.GranulariteDate;
import com.batulo.enums.TypeQuestion;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Statistiques de base d'un sondage (module 4.3) : résumé et, pour chaque question, le bloc de son type
 * (options, numerique, dates, classement, matrice ou textes). Pourcentages calculés sur les répondants à la question.
 */
public record StatistiquesResponse(
        Long sondageId,
        String titre,
        /** true : toutes les versions du sondage ; false : cette version seulement. */
        boolean lignee,
        List<Integer> versions,
        LocalDate du,
        LocalDate au,
        Resume resume,
        List<QuestionStat> questions
) {
    public record Resume(long nombreReponses, Instant premiereReponse, Instant derniereReponse) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
public record QuestionStat(
            Long questionId,
            String cle,
            String section,
            String libelle,
            TypeQuestion type,
            long nombreRepondants,
            long sansReponse,
            /** CHOIX_UNIQUE, CHOIX_MULTIPLE, OUI_NON. */
            List<OptionStat> options,
            /** ECHELLE, NUMERIQUE. */
            Numerique numerique,
            /** DATE. */
            Dates dates,
            /** CLASSEMENT, du mieux classé au moins bien classé. */
            List<RangStat> classement,
            /** MATRICE. */
            Matrice matrice,
            /** TEXTE_COURT, TEXTE_LONG : première page, les plus récents d'abord. */
            PageTextes textes
    ) {
    }

    public record OptionStat(String valeur, String libelle, long nombre, BigDecimal pourcentage) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
public record Numerique(BigDecimal moyenne, BigDecimal mediane, BigDecimal min, BigDecimal max,
                            List<Classe> distribution) {
    }

    /** Une valeur (« valeur ») ou une tranche [de, a[ (la dernière inclut « a »). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
public record Classe(BigDecimal valeur, BigDecimal de, BigDecimal a, long nombre, BigDecimal pourcentage) {
    }

    public record Dates(LocalDate min, LocalDate max, GranulariteDate granularite, List<Periode> repartition) {
    }

    /** « 2026-07-14 » (jour) ou « 2026-07 » (mois). */
    public record Periode(String periode, long nombre) {
    }

    public record RangStat(String valeur, String libelle, BigDecimal rangMoyen, long nombrePremier) {
    }

    public record Matrice(List<String> colonnes, List<LigneMatrice> lignes) {
    }

    public record LigneMatrice(String ligne, long nombre, List<Cellule> cellules) {
    }

    public record Cellule(String colonne, long nombre, BigDecimal pourcentage) {
    }

    public record PageTextes(List<Texte> contenu, int page, int taille, long totalElements, int totalPages) {
    }

    public record Texte(String texte, Instant dateSoumission) {
    }
}
