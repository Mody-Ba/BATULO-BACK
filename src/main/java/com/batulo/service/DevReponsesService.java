package com.batulo.service;

/** Outil de développement (profil dev uniquement) : remplir un sondage de réponses de test. */
public interface DevReponsesService {

    /**
     * Génère des réponses aléatoires mais valides (types, configuration, questions obligatoires) à un sondage
     * EN_COURS visible par l'utilisateur, datées sur les 30 derniers jours.
     *
     * @return nombre de réponses créées
     * @throws com.batulo.exception.ConflitException SONDAGE_NON_EN_COURS
     */
    int genererReponses(Long utilisateurId, Long sondageId, int nombre);
}
