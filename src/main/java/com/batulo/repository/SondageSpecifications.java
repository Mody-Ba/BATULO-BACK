package com.batulo.repository;

import com.batulo.entity.Assignation;
import com.batulo.entity.Sondage;
import com.batulo.enums.StatutSondage;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
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

    /**
     * Sondages visibles par un gestionnaire (GE-04, RG-02) : la LIGNÉE est visible s'il a créé l'une de ses versions
     * ou si elle lui est assignée. Jamais ceux d'un autre gestionnaire.
     */
    public static Specification<Sondage> visiblesParGestionnaire(Long gestionnaireId) {
        return (root, query, cb) -> {
            Expression<Long> lignee = cb.coalesce(root.get("lignee").<Long>get("id"), root.<Long>get("id"));

            Subquery<Long> creees = query.subquery(Long.class);
            Root<Sondage> version = creees.from(Sondage.class);
            creees.select(cb.coalesce(version.get("lignee").<Long>get("id"), version.<Long>get("id")))
                    .where(cb.equal(version.get("creePar").get("id"), gestionnaireId));

            Subquery<Long> assignees = query.subquery(Long.class);
            Root<Assignation> assignation = assignees.from(Assignation.class);
            assignees.select(assignation.get("sondage").<Long>get("id"))
                    .where(cb.equal(assignation.get("gestionnaire").get("id"), gestionnaireId));

            return cb.or(lignee.in(creees), lignee.in(assignees));
        };
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
