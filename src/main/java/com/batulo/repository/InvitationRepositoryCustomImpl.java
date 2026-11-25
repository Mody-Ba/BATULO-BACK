package com.batulo.repository;

import com.batulo.entity.Invitation;
import com.batulo.enums.StatutInvitation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Selection;

import java.util.EnumMap;
import java.util.Map;

/** Implémentation de {@link InvitationRepositoryCustom}, rattachée à InvitationRepository par Spring Data. */
public class InvitationRepositoryCustomImpl implements InvitationRepositoryCustom {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Map<StatutInvitation, Long> compterParStatut(Long ligneeId) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        var query = cb.createQuery(Object[].class);
        var invitation = query.from(Invitation.class);
        StatutInvitation[] statuts = StatutInvitation.values();
        Selection<?>[] sommes = new Selection<?>[statuts.length];
        for (int i = 0; i < statuts.length; i++) {
            sommes[i] = cb.sum(cb.<Long>selectCase()
                    .when(InvitationSpecifications.condition(cb, invitation, statuts[i]), 1L)
                    .otherwise(0L));
        }
        query.multiselect(sommes).where(cb.equal(invitation.get("lignee").get("id"), ligneeId));
        Object[] ligne = entityManager.createQuery(query).getSingleResult();
        Map<StatutInvitation, Long> resultat = new EnumMap<>(StatutInvitation.class);
        for (int i = 0; i < statuts.length; i++) {
            resultat.put(statuts[i], ligne[i] == null ? 0L : ((Number) ligne[i]).longValue());
        }
        return resultat;
    }
}
