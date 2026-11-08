package com.batulo.service;

import com.batulo.enums.GranulariteDate;
import com.batulo.service.model.CritereModel;
import com.batulo.service.model.CroisementModel;
import com.batulo.service.model.StatistiquesModel;
import com.batulo.service.model.StatistiquesModel.PageTextes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Statistiques de base d'un sondage (module 4.3, AD-13, GE-05). Admin : tous les sondages de l'entreprise ;
 * gestionnaire : ceux qu'il a créés ou qui lui sont assignés (RG-02) ; sinon 404. Calculs en SQL.
 */
public interface StatistiqueService {

    /**
     * @param lignee true : toutes les versions du sondage, regroupées par clé de question ; false : cette version
     * @param du     facultatif, inclus (heure de batulo.fuseau-horaire)
     * @param au     facultatif, inclus
     * @param granulariteDate facultative : JOUR ou MOIS pour les questions DATE (automatique sinon)
     * @param criteres filtres de segmentation « clé de critère -> valeurs » (OU dans une clé, ET entre clés) ;
     *                 vide : tous les répondants
     * @throws com.batulo.exception.RegleGestionException PERIODE_INVALIDE si du est après au ; CRITERE_INVALIDE
     */
    StatistiquesModel statistiques(Long utilisateurId, Long sondageId, boolean lignee, LocalDate du, LocalDate au,
                                   GranulariteDate granulariteDate, Map<String, List<String>> criteres);

    /** Critères d'analyse du sondage (ou de la lignée) et leurs valeurs, pour construire les filtres. */
    List<CritereModel> criteres(Long utilisateurId, Long sondageId, boolean lignee);

    /**
     * Tableau croisé d'une question (choix, oui/non, échelle, numérique) par un critère.
     *
     * @throws com.batulo.exception.RegleGestionException CRITERE_INVALIDE, CROISEMENT_INVALIDE
     * @throws com.batulo.exception.ResourceNotFoundException QUESTION_INTROUVABLE
     */
    CroisementModel croisement(Long utilisateurId, Long sondageId, String cleQuestion, String clePar, boolean lignee,
                               LocalDate du, LocalDate au, Map<String, List<String>> criteres);

    /**
     * Textes d'une question TEXTE_COURT ou TEXTE_LONG, les plus récents d'abord, avec les mêmes filtres.
     *
     * @throws com.batulo.exception.ResourceNotFoundException QUESTION_INTROUVABLE
     * @throws com.batulo.exception.RegleGestionException QUESTION_NON_TEXTE
     */
    PageTextes textes(Long utilisateurId, Long sondageId, Long questionId, boolean lignee, LocalDate du, LocalDate au,
                      Map<String, List<String>> criteres, int page, int taille);
}
