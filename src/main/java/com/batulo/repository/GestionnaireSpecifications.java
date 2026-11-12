package com.batulo.repository;

import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import org.springframework.data.jpa.domain.Specification;

/** Filtres de la liste des gestionnaires de l'admin. */
public final class GestionnaireSpecifications {

    /** Lettres accentuées (minuscules) et leur équivalent sans accent : même résultat que NormaliseurRecherche. */
    private static final String AVEC_ACCENTS = "àâäáãåéèêëíìîïóòôöõúùûüçñÿý";
    private static final String SANS_ACCENTS = "aaaaaaeeeeiiiiooooouuuucnyy";
    private static final char ECHAPPEMENT = '\\';

    private GestionnaireSpecifications() {
    }

    public static Specification<Utilisateur> gestionnairesDe(Long entrepriseId) {
        return (root, query, cb) -> cb.and(cb.equal(root.get("entreprise").get("id"), entrepriseId),
                cb.equal(root.get("role"), Role.GESTIONNAIRE));
    }

    /** @param actif null : aucun filtre */
    public static Specification<Utilisateur> actif(Boolean actif) {
        if (actif == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> cb.equal(root.get("actif"), actif);
    }

    /**
     * Prénom, nom ou courriel contenant le terme, sans tenir compte des majuscules ni des accents.
     *
     * @param termeNormalise terme déjà passé par NormaliseurRecherche ; null : aucun filtre
     */
    public static Specification<Utilisateur> recherche(String termeNormalise) {
        if (termeNormalise == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        String motif = "%" + echapper(termeNormalise) + "%";
        return (root, query, cb) -> cb.or(
                cb.like(sansAccents(cb, root.get("prenom")), motif, ECHAPPEMENT),
                cb.like(sansAccents(cb, root.get("nom")), motif, ECHAPPEMENT),
                cb.like(sansAccents(cb, root.get("courriel")), motif, ECHAPPEMENT));
    }

    private static Expression<String> sansAccents(CriteriaBuilder cb, Expression<String> colonne) {
        return cb.function("translate", String.class, cb.lower(colonne), cb.literal(AVEC_ACCENTS),
                cb.literal(SANS_ACCENTS));
    }

    /** Les jokers saisis par l'utilisateur (% et _) sont cherchés tels quels. */
    private static String echapper(String terme) {
        return terme.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
