package com.batulo.repository;

import com.batulo.entity.Abonnement;
import com.batulo.enums.StatutAbonnement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Selection;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/** Implémentation de {@link AbonnementRepositoryCustom}, rattachée à AbonnementRepository par Spring Data. */
public class AbonnementRepositoryCustomImpl implements AbonnementRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Map<StatutAbonnement, Long> compterParStatut(Instant maintenant, int joursAlerte) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(Object[].class);
        var abonnement = query.from(Abonnement.class);
        var entreprise = abonnement.join("entreprise");
        StatutAbonnement[] statuts = StatutAbonnement.values();

        // sum(case when <statut> then 1 else 0 end) pour chaque statut.
        Selection<?>[] sommes = new Selection<?>[statuts.length];
        for (int i = 0; i < statuts.length; i++) {
            sommes[i] = cb.sum(cb.<Long>selectCase()
                    .when(AbonnementSpecifications.condition(cb, statuts[i], abonnement.get("dateExpiration"),
                            entreprise.get("actif"), maintenant, joursAlerte), 1L)
                    .otherwise(0L));
        }
        Object[] ligne = entityManager.createQuery(query.multiselect(sommes)).getSingleResult();

        Map<StatutAbonnement, Long> resultat = new EnumMap<>(StatutAbonnement.class);
        for (int i = 0; i < statuts.length; i++) {
            // sum() vaut null quand il n'y a aucun abonnement.
            resultat.put(statuts[i], ligne[i] == null ? 0L : ((Number) ligne[i]).longValue());
        }
        return resultat;
    }
}
