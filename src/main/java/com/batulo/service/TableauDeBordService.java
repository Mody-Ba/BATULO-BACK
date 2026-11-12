package com.batulo.service;

import com.batulo.service.model.TableauDeBordModel;

/** Tableau de bord de l'admin (abonnement, gestionnaires, sondages, réponses de son entreprise). */
public interface TableauDeBordService {

    /** Uniquement des comptages SQL : aucune ligne n'est chargée en mémoire. */
    TableauDeBordModel tableauDeBord(Long entrepriseId);
}
