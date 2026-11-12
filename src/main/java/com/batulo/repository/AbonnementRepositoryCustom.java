package com.batulo.repository;

import com.batulo.enums.StatutAbonnement;

import java.time.Instant;
import java.util.Map;

/** Agrégations sur les abonnements écrites avec l'API Criteria. */
public interface AbonnementRepositoryCustom {

    /**
     * Nombre d'abonnements par statut effectif (même calcul que AbonnementSpecifications), en une requête.
     *
     * @return chaque statut, 0 compris
     */
    Map<StatutAbonnement, Long> compterParStatut(Instant maintenant, int joursAlerte);
}
