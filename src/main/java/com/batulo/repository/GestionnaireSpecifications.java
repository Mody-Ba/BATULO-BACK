package com.batulo.repository;

import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import org.springframework.data.jpa.domain.Specification;

/** Filtres de la liste des gestionnaires de l'admin. */
public final class GestionnaireSpecifications {

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
        return (root, query, cb) -> RechercheTexte.contient(cb, termeNormalise,
                root.<String>get("prenom"), root.<String>get("nom"), root.<String>get("courriel"));
    }
}
