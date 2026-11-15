package com.batulo.service.model;

import com.batulo.enums.Formule;

/** Demande de paiement mobile d'une formule ; téléphone facultatif (8 chiffres). */
public record DemandePaiementMobileModel(Formule formule, String telephone) {
}
