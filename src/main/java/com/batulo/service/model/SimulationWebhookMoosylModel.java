package com.batulo.service.model;

import com.batulo.enums.StatutPaiement;

/** Résultat d'un webhook simulé : réponse du backend local et statut du paiement après traitement. */
public record SimulationWebhookMoosylModel(
        String reference,
        String evenement,
        String statutEnvoye,
        int statutHttp,
        String reponse,
        StatutPaiement statutPaiement
) {
}
