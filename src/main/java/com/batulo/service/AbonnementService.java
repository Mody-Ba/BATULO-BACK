package com.batulo.service;

import com.batulo.service.model.AbonnementModel;
import com.batulo.service.model.ConfirmationPaiementModel;
import com.batulo.service.model.DemandeIntentionPaiementModel;
import com.batulo.service.model.FormuleModel;
import com.batulo.service.model.IntentionPaiementModel;
import com.batulo.service.model.PaiementConfirmeModel;

import java.util.List;

/**
 * Abonnement d'une entreprise et paiement en ligne (RG-08 à RG-10). Un abonnement ne devient ACTIF que par un
 * paiement Stripe revérifié côté serveur : il n'existe ni annulation ni activation manuelle.
 */
public interface AbonnementService {

    /** Formules proposées et leurs prix (application.yml). */
    List<FormuleModel> formules();

    /** @throws com.batulo.exception.ResourceNotFoundException aucun abonnement pour cette entreprise */
    AbonnementModel monAbonnement(Long entrepriseId);

    /**
     * Crée un PaymentIntent Stripe pour la formule choisie et enregistre un paiement EN_ATTENTE.
     *
     * @throws com.batulo.exception.ResourceNotFoundException aucun abonnement pour cette entreprise
     * @throws com.batulo.exception.RegleGestionException entreprise désactivée
     */
    IntentionPaiementModel creerIntentionPaiement(Long entrepriseId, DemandeIntentionPaiementModel demande);

    /**
     * Revérifie le PaymentIntent auprès de Stripe (statut succeeded, entreprise, montant, devise), enregistre le
     * paiement et prolonge l'abonnement. Idempotent : un paiement déjà confirmé n'est jamais compté deux fois.
     *
     * @throws com.batulo.exception.ResourceNotFoundException paiement inconnu pour cette entreprise
     * @throws com.batulo.exception.RegleGestionException paiement pas (encore) réussi ou incohérent
     */
    PaiementConfirmeModel confirmerPaiement(Long entrepriseId, ConfirmationPaiementModel confirmation);
}
