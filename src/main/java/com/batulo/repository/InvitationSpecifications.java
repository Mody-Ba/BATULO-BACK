package com.batulo.repository;

import com.batulo.entity.Invitation;
import com.batulo.enums.StatutInvitation;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * Filtres des invitations. {@link #condition} est la seule traduction SQL du statut calculé (RegleInvitation) :
 * REVOQUEE, A_REPONDU, OUVERTE, EN_ECHEC, INVITEE, A_INVITER, dans cet ordre de priorité.
 */
public final class InvitationSpecifications {

    private InvitationSpecifications() {
    }

    public static Specification<Invitation> deLaLignee(Long ligneeId) {
        return (root, query, cb) -> cb.equal(root.get("lignee").get("id"), ligneeId);
    }

    public static Specification<Invitation> parStatut(StatutInvitation statut) {
        return (root, query, cb) -> statut == null ? cb.conjunction() : condition(cb, root, statut);
    }

    /** Prénom, nom ou courriel contient le terme (sans majuscules ni accents), terme déjà normalisé. */
    public static Specification<Invitation> recherche(String termeNormalise) {
        return (root, query, cb) -> termeNormalise == null || termeNormalise.isBlank() ? cb.conjunction()
                : RechercheTexte.contient(cb, termeNormalise, root.get("prenom"), root.get("nom"), root.get("courriel"));
    }

    public static Predicate condition(CriteriaBuilder cb, Root<Invitation> root, StatutInvitation statut) {
        Path<Instant> revoquee = root.get("revoqueeLe");
        Path<Instant> repondu = root.get("reponduLe");
        Path<Instant> ouverte = root.get("ouverteLe");
        Path<Instant> echec = root.get("echecEnvoiLe");
        Path<Instant> envoi = root.get("premierEnvoiLe");
        return switch (statut) {
            case REVOQUEE -> cb.isNotNull(revoquee);
            case A_REPONDU -> cb.and(cb.isNull(revoquee), cb.isNotNull(repondu));
            case OUVERTE -> cb.and(cb.isNull(revoquee), cb.isNull(repondu), cb.isNotNull(ouverte));
            case EN_ECHEC -> cb.and(cb.isNull(revoquee), cb.isNull(repondu), cb.isNull(ouverte), cb.isNotNull(echec));
            case INVITEE -> cb.and(cb.isNull(revoquee), cb.isNull(repondu), cb.isNull(ouverte), cb.isNull(echec),
                    cb.isNotNull(envoi));
            case A_INVITER -> cb.and(cb.isNull(revoquee), cb.isNull(repondu), cb.isNull(ouverte), cb.isNull(echec),
                    cb.isNull(envoi));
        };
    }
}
