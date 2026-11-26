package com.batulo.service.model;

import com.batulo.enums.TypePaiement;
/** Résultat d'une confirmation : l'abonnement à jour et si le paiement avait déjà été pris en compte. */
public record PaiementConfirmeModel(
        AbonnementModel abonnement,
        boolean dejaConfirme,
        TypePaiement type,
        Integer quantite,
        CreditsModel credits
) {
}
