package com.batulo.service.model;

/** Résultat d'une confirmation : l'abonnement à jour et si le paiement avait déjà été pris en compte. */
public record PaiementConfirmeModel(
        AbonnementModel abonnement,
        boolean dejaConfirme
) {
}
