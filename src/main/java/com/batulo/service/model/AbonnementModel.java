package com.batulo.service.model;

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
        String devise
) {
}
