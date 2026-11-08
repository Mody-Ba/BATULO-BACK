package com.batulo.service;

import com.batulo.enums.GranulariteDate;
import com.batulo.service.model.StatistiquesModel;
import com.batulo.service.model.StatistiquesModel.PageTextes;

import java.time.LocalDate;

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
     * @throws com.batulo.exception.RegleGestionException PERIODE_INVALIDE si du est après au
     */
    StatistiquesModel statistiques(Long utilisateurId, Long sondageId, boolean lignee, LocalDate du, LocalDate au,
                                   GranulariteDate granulariteDate);

    /**
     * Textes d'une question TEXTE_COURT ou TEXTE_LONG, les plus récents d'abord, avec les mêmes filtres.
     *
     * @throws com.batulo.exception.ResourceNotFoundException QUESTION_INTROUVABLE
     * @throws com.batulo.exception.RegleGestionException QUESTION_NON_TEXTE
     */
    PageTextes textes(Long utilisateurId, Long sondageId, Long questionId, boolean lignee, LocalDate du, LocalDate au,
                      int page, int taille);
}
