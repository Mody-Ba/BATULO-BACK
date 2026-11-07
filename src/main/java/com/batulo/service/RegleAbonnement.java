package com.batulo.service;

import com.batulo.entity.Abonnement;
import com.batulo.enums.Formule;
import com.batulo.enums.StatutAbonnement;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Règles de calcul d'un abonnement (section 5 du cahier des charges). Le statut se déduit des dates :
 * il reste exact même si la tâche quotidienne (étape 6) n'est pas encore passée.
 */
public final class RegleAbonnement {

    private static final long SECONDES_PAR_JOUR = 86_400;

    private RegleAbonnement() {
    }

    /** Fin d'une période qui commence à {@code debut} : 1 mois ou 1 an, en UTC. */
    public static Instant ajouterDuree(Formule formule, Instant debut) {
        var date = debut.atZone(ZoneOffset.UTC);
        return switch (formule) {
            case MENSUEL -> date.plusMonths(1).toInstant();
            case ANNUEL -> date.plusYears(1).toInstant();
        };
    }

    /**
     * Début de la nouvelle période payée : un renouvellement avant l'échéance prolonge depuis la date
     * d'expiration (aucun jour perdu) ; après l'échéance ou au premier paiement, la période part du paiement.
     */
    public static Instant debutNouvellePeriode(Abonnement abonnement, Instant maintenant) {
        Instant expiration = abonnement.getDateExpiration();
        return expiration != null && expiration.isAfter(maintenant) ? expiration : maintenant;
    }

    public static StatutAbonnement statutEffectif(Abonnement abonnement, Instant maintenant, int joursAlerte) {
        if (!abonnement.getEntreprise().isActif()) {
            return StatutAbonnement.DESACTIVE;
        }
        Instant expiration = abonnement.getDateExpiration();
        if (expiration == null) {
            return StatutAbonnement.EN_ATTENTE_PAIEMENT;
        }
        if (!maintenant.isBefore(expiration)) {
            return StatutAbonnement.EXPIRE;
        }
        if (!expiration.isAfter(maintenant.plus(Duration.ofDays(joursAlerte)))) {
            return StatutAbonnement.EXPIRE_BIENTOT;
        }
        return StatutAbonnement.ACTIF;
    }

    /** Jours restants avant l'expiration (arrondi au jour supérieur), 0 si expiré, null si jamais payé. */
    public static Long joursRestants(Abonnement abonnement, Instant maintenant) {
        Instant expiration = abonnement.getDateExpiration();
        if (expiration == null) {
            return null;
        }
        long secondes = Duration.between(maintenant, expiration).getSeconds();
        return secondes <= 0 ? 0L : (secondes + SECONDES_PAR_JOUR - 1) / SECONDES_PAR_JOUR;
    }
}
