package com.batulo.service.model;

/** Achat de sondages : quantité (1 à 1000, contrôlée par le service) et téléphone facultatif (paiement mobile). */
public record DemandeAchatSondagesModel(Integer quantite, String telephone) {
}
