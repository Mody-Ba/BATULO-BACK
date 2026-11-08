package com.batulo.repository;

import com.batulo.entity.Sondage;
import com.batulo.enums.StatutSondage;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * Filtres de la liste des sondages. Le filtre par statut traduit en SQL la règle de RegleSondage :
 * les deux doivent rester cohérents.
 */
public final class SondageSpecifications {

    private SondageSpecifications() {
    }

    public static Specification<Sondage> deLEntreprise(Long entrepriseId) {
        return (root, query, cb) -> cb.equal(root.get("entreprise").get("id"), entrepriseId);
    }

    public static Specification<Sondage> creePar(Long utilisateurId) {
        return (root, query, cb) -> cb.equal(root.get("creePar").get("id"), utilisateurId);
    }

    public static Specification<Sondage> modelesSeulement() {
        return (root, query, cb) -> cb.isTrue(root.get("estModele"));
    }

    /** @param statut null : aucun filtre */
    public static Specification<Sondage> parStatut(StatutSondage statut, Instant maintenant) {
        if (statut == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            var publication = root.<Instant>get("datePublication");
            var fermeture = root.<Instant>get("dateFermeture");
            var fermeLe = root.<Instant>get("fermeLe");
            var pasEncoreFerme = cb.and(cb.isNull(fermeLe),
                    cb.or(cb.isNull(fermeture), cb.greaterThan(fermeture, maintenant)));
            return switch (statut) {
                case NON_COMMENCE -> cb.or(cb.isNull(publication),
                        cb.and(cb.greaterThan(publication, maintenant), pasEncoreFerme));
                case EN_COURS -> cb.and(cb.lessThanOrEqualTo(publication, maintenant), pasEncoreFerme);
                case TERMINE -> cb.and(cb.isNotNull(publication),
                        cb.or(cb.isNotNull(fermeLe), cb.lessThanOrEqualTo(fermeture, maintenant)));
            };
        };
    }
}
