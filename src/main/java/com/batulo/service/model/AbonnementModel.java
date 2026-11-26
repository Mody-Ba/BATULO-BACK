package com.batulo.service.model;

import com.batulo.enums.OffreInscription;
import com.batulo.enums.Formule;
import com.batulo.enums.StatutAbonnement;

import java.math.BigDecimal;
import java.time.Instant;

/** Abonnement d'une entreprise, avec le statut recalculé à partir des dates et les jours restants. */
public record AbonnementModel(
        Long id,
        Long entrepriseId,
        String entrepriseNom,
        Formule formule,
        StatutAbonnement statut,
        Instant dateDebut,
        Instant dateExpiration,
        Long joursRestants,
        BigDecimal montant,
        String devise,
        /** Choix fait à l'inscription (null : aucun). */
        OffreInscription offreChoisie,
        Integer nombreSondagesChoisi,
        /** Sondages achetés ; rempli pour un seul abonnement (le mien, la fiche du super admin), null dans les listes. */
        CreditsModel credits
) {
    public AbonnementModel avecCredits(CreditsModel credits) {
        return new AbonnementModel(id, entrepriseId, entrepriseNom, formule, statut, dateDebut, dateExpiration,
                joursRestants, montant, devise, offreChoisie, nombreSondagesChoisi, credits);
    }
}
