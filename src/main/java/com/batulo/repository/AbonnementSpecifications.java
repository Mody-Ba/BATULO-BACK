package com.batulo.repository;

import com.batulo.entity.Abonnement;
import com.batulo.enums.StatutAbonnement;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Duration;
import java.time.Instant;

/**
 * Filtre SQL équivalent au statut effectif d'un abonnement, calculé à partir des dates et de l'état
 * de l'entreprise (le statut stocké peut avoir un retard d'au plus un passage de la tâche quotidienne).
 * {@link #condition} est la seule traduction SQL de RegleAbonnement.statutEffectif : liste des abonnements,
 * liste des admins et tableau de bord du super admin l'utilisent tous.
 */
public final class AbonnementSpecifications {

    private AbonnementSpecifications() {
    }

    /** @param statut null : aucun filtre */
    public static Specification<Abonnement> parStatut(StatutAbonnement statut, Instant maintenant, int joursAlerte) {
        if (statut == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> condition(cb, statut, root.get("dateExpiration"),
                root.get("entreprise").get("actif"), maintenant, joursAlerte);
    }

    /**
     * Condition « l'abonnement a ce statut effectif ».
     *
     * @param expiration      date d'expiration de l'abonnement
     * @param entrepriseActive état de l'entreprise de l'abonnement
     */
    public static Predicate condition(CriteriaBuilder cb, StatutAbonnement statut, Expression<Instant> expiration,
                                      Expression<Boolean> entrepriseActive, Instant maintenant, int joursAlerte) {
        Instant seuil = maintenant.plus(Duration.ofDays(joursAlerte));
        Predicate active = cb.isTrue(entrepriseActive);
        return switch (statut) {
            case DESACTIVE -> cb.isFalse(entrepriseActive);
            case EN_ATTENTE_PAIEMENT -> cb.and(active, cb.isNull(expiration));
            case EXPIRE -> cb.and(active, cb.lessThanOrEqualTo(expiration, maintenant));
            case EXPIRE_BIENTOT -> cb.and(active, cb.greaterThan(expiration, maintenant),
                    cb.lessThanOrEqualTo(expiration, seuil));
            case ACTIF -> cb.and(active, cb.greaterThan(expiration, seuil));
        };
    }
}
