package com.batulo.service;

import com.batulo.enums.Langue;
import com.batulo.service.model.FormulairePublicModel;
import com.batulo.service.model.SoumissionModel;

import java.time.Instant;

/**
 * Collecte publique, sans compte (RE-01 à RE-03). Le jeton du lien désigne la lignée : on répond à la version
 * EN_COURS du moment. Ordre des contrôles : jeton inconnu (404 SONDAGE_INTROUVABLE), abonnement ou entreprise
 * inactifs (410 SONDAGE_FERME), ouverture planifiée (403 SONDAGE_NON_OUVERT), terminé (410 SONDAGE_FERME).
 */
public interface CollecteService {

    /**
     * @param jetonRepondant jeton de l'appareil (en-tête X-Jeton-Repondant), facultatif ; sert seulement à
     *                       calculer dejaRepondu pour un sondage à réponse unique (absent ou mal formé : false)
     * @param langue         langue demandée ; non proposée par la version ouverte (ou null) : langue préférée de
     *                       l'invité, sinon langue principale
     * @param jetonInvitation jeton du lien personnel (en-tête X-Jeton-Invitation), facultatif : 404
     *                       INVITATION_INVALIDE, 410 INVITATION_REVOQUEE ; la première ouverture est enregistrée
     */
    FormulairePublicModel formulaire(String jetonPublic, String jetonRepondant, Langue langue, String jetonInvitation);

    /**
     * Valide et enregistre les réponses à la version ouverte.
     *
     * @return date d'enregistrement
     * @throws com.batulo.exception.ReponsesInvalidesException une erreur par question (400)
     * @throws com.batulo.exception.ConflitException DEJA_REPONDU : cet appareil a déjà répondu (409)
     */
    Instant soumettre(String jetonPublic, SoumissionModel soumission);
}
