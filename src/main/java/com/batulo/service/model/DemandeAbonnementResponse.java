package com.batulo.service.model;

import com.batulo.enums.Formule;
import com.batulo.enums.StatutAbonnement;
import com.batulo.enums.StatutDemande;

import java.time.Instant;

/** Demande d'augmentation d'abonnement (AD-05, SA-07). */
public record DemandeAbonnementResponse(
        Long id,
        Long entrepriseId,
        String entreprise,
        Long adminId,
        String adminNom,
        String adminCourriel,
        Formule formuleSouhaitee,
        /** Formule payée actuellement ; null si jamais payé. */
        Formule formuleActuelle,
        StatutAbonnement statutAbonnement,
        String message,
        StatutDemande statut,
        String reponse,
        String traiteeParNom,
        Instant dateDemande,
        Instant dateTraitement
) {
}
