package com.batulo.service.model;

import com.batulo.enums.Formule;

import java.math.BigDecimal;

/** Une formule d'abonnement et son prix (lu dans application.yml). */
public record FormuleModel(
        Formule formule,
        String libelle,
        BigDecimal prix,
        String devise,
        String duree
) {
}
