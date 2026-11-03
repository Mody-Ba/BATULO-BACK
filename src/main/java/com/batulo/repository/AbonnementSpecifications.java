package com.batulo.repository;

import com.batulo.entity.Abonnement;
import com.batulo.enums.StatutAbonnement;
import org.springframework.data.jpa.domain.Specification;

import java.time.Duration;
import java.time.Instant;

/**
 * Filtre SQL équivalent au statut effectif d'un abonnement, calculé à partir des dates et de l'état
 * de l'entreprise (le statut stocké peut avoir un retard d'au plus un passage de la tâche quotidienne).
 */
public final class AbonnementSpecifications {

    private AbonnementSpecifications() {
    }

    /** @param statut null : aucun filtre */
    public static Specification<Abonnement> parStatut(StatutAbonnement statut, Instant maintenant, int joursAlerte) {
        if (statut == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        Instant seuil = maintenant.plus(Duration.ofDays(joursAlerte));
        return (root, query, cb) -> {
            var entrepriseActive = cb.isTrue(root.get("entreprise").get("actif"));
            var expiration = root.<Instant>get("dateExpiration");
            return switch (statut) {
                case DESACTIVE -> cb.isFalse(root.get("entreprise").get("actif"));
                case EN_ATTENTE_PAIEMENT -> cb.and(entrepriseActive, cb.isNull(expiration));
                case EXPIRE -> cb.and(entrepriseActive, cb.lessThanOrEqualTo(expiration, maintenant));
                case EXPIRE_BIENTOT -> cb.and(entrepriseActive, cb.greaterThan(expiration, maintenant),
                        cb.lessThanOrEqualTo(expiration, seuil));
                case ACTIF -> cb.and(entrepriseActive, cb.greaterThan(expiration, seuil));
            };
        };
    }
}
