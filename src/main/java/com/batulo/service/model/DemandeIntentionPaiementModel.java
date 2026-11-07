package com.batulo.service.model;

import com.batulo.enums.Formule;

/** Formule choisie par l'admin, transmise au service. */
public record DemandeIntentionPaiementModel(
        Formule formule
) {
}
