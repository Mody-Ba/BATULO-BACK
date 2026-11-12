package com.batulo.repository;

import com.batulo.entity.Abonnement;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import com.batulo.enums.StatutAbonnement;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/** Filtres de la liste des admins du super admin. */
public final class AdminSpecifications {

    private AdminSpecifications() {
    }

    public static Specification<Utilisateur> admins() {
        return (root, query, cb) -> cb.equal(root.get("role"), Role.ADMIN);
    }

    /**
     * Admins dont l'abonnement de l'entreprise a ce statut effectif (même calcul que la liste des abonnements).
     *
     * @param statut null : aucun filtre
     */
    public static Specification<Utilisateur> statutAbonnement(StatutAbonnement statut, Instant maintenant,
                                                               int joursAlerte) {
        if (statut == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            var sousRequete = query.subquery(Long.class);
            var abonnement = sousRequete.from(Abonnement.class);
            var entreprise = abonnement.join("entreprise");
            sousRequete.select(abonnement.get("id")).where(
                    cb.equal(entreprise.get("id"), root.get("entreprise").get("id")),
                    AbonnementSpecifications.condition(cb, statut, abonnement.get("dateExpiration"),
                            entreprise.get("actif"), maintenant, joursAlerte));
            return cb.exists(sousRequete);
        };
    }

    /**
     * Nom de l'entreprise, prénom, nom ou courriel de l'admin contenant le terme, sans majuscules ni accents.
     *
     * @param termeNormalise terme déjà passé par NormaliseurRecherche ; null : aucun filtre
     */
    public static Specification<Utilisateur> recherche(String termeNormalise) {
        if (termeNormalise == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> RechercheTexte.contient(cb, termeNormalise,
                root.get("entreprise").<String>get("nom"), root.<String>get("prenom"), root.<String>get("nom"),
                root.<String>get("courriel"));
    }
}
