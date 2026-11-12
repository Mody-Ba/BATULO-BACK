package com.batulo.repository;

import com.batulo.entity.Assignation;
import com.batulo.entity.Sondage;
import com.batulo.enums.StatutSondage;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
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

    /**
     * Une ligne par lignée (?derniereVersion=true, tableau de bord) : la dernière version publiée et démarrée
     * (EN_COURS ou TERMINE) si la lignée en a une, sinon sa dernière version (brouillon, ou version 1 planifiée).
     * Une version planifiée ou un brouillon plus récents que la version démarrée ne la remplacent donc pas.
     */
    public static Specification<Sondage> representantDeLignee(Instant maintenant) {
        return (root, query, cb) -> {
            Expression<Long> lignee = idLignee(root, cb);
            Path<Integer> version = root.get("version");

            Subquery<Long> demarreePlusRecente = query.subquery(Long.class);
            Root<Sondage> v1 = demarreePlusRecente.from(Sondage.class);
            demarreePlusRecente.select(v1.get("id")).where(dansLaLignee(v1, lignee, cb),
                    demarree(v1, query, cb, maintenant), cb.greaterThan(v1.get("version"), version));

            Subquery<Long> uneDemarree = query.subquery(Long.class);
            Root<Sondage> v2 = uneDemarree.from(Sondage.class);
            uneDemarree.select(v2.get("id")).where(dansLaLignee(v2, lignee, cb), demarree(v2, query, cb, maintenant));

            Subquery<Long> plusRecente = query.subquery(Long.class);
            Root<Sondage> v3 = plusRecente.from(Sondage.class);
            plusRecente.select(v3.get("id")).where(dansLaLignee(v3, lignee, cb),
                    cb.greaterThan(v3.get("version"), version));

            return cb.or(
                    cb.and(demarree(root, query, cb, maintenant), cb.not(cb.exists(demarreePlusRecente))),
                    cb.and(cb.not(demarree(root, query, cb, maintenant)), cb.not(cb.exists(uneDemarree)),
                            cb.not(cb.exists(plusRecente))));
        };
    }

    /** Id de la lignée : celui de la version 1 (lignee_id est null pour elle). */
    private static Expression<Long> idLignee(Root<Sondage> root, CriteriaBuilder cb) {
        return cb.coalesce(root.get("lignee").<Long>get("id"), root.<Long>get("id"));
    }

    /** Écrit sans coalesce pour profiter des index (clé primaire et idx_sondage_lignee). */
    private static Predicate dansLaLignee(Root<Sondage> version, Expression<Long> lignee, CriteriaBuilder cb) {
        return cb.or(cb.equal(version.get("id"), lignee), cb.equal(version.get("lignee").get("id"), lignee));
    }

    /** EN_COURS ou TERMINE : le contraire de NON_COMMENCE, avec la même règle que le filtre par statut. */
    private static Predicate demarree(Root<Sondage> version, CriteriaQuery<?> query, CriteriaBuilder cb,
                                      Instant maintenant) {
        return cb.not(parStatut(StatutSondage.NON_COMMENCE, maintenant).toPredicate(version, query, cb));
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
