package com.batulo.service;

import com.batulo.service.model.AbonnementModel;
import com.batulo.service.model.ConfirmationPaiementModel;
import com.batulo.service.model.CreditsModel;
import com.batulo.service.model.DemandeAchatSondagesModel;
import com.batulo.service.model.DemandeIntentionPaiementModel;
import com.batulo.service.model.FormuleModel;
import com.batulo.service.model.IntentionPaiementModel;
import com.batulo.service.model.PaiementConfirmeModel;
import com.batulo.service.model.PaiementModel;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

/**
 * Abonnement d'une entreprise et paiement en ligne (RG-08 à RG-10). Un abonnement ne devient ACTIF que par un
 * paiement Stripe revérifié côté serveur : il n'existe ni annulation ni activation manuelle.
 */
public interface AbonnementService {

    /** Formules proposées et leurs prix (application.yml), puis l'achat de sondages (prix d'un sondage). */
    List<FormuleModel> formules();

    /** @throws com.batulo.exception.ResourceNotFoundException aucun abonnement pour cette entreprise */
    AbonnementModel monAbonnement(Long entrepriseId);

    /** Comme {@link #monAbonnement}, mais vide si l'entreprise n'a pas d'abonnement. */
    Optional<AbonnementModel> trouverAbonnement(Long entrepriseId);

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

    /**
     * Achat de sondages par carte : PaymentIntent de quantité × prix unitaire et paiement EN_ATTENTE ; confirmé par
     * {@link #confirmerPaiement}, qui crédite le solde une seule fois.
     *
     * @throws com.batulo.exception.RegleGestionException QUANTITE_INVALIDE (hors de 1 à 1000), entreprise désactivée
     */
    IntentionPaiementModel creerIntentionAchat(Long entrepriseId, DemandeAchatSondagesModel demande);

    /** Solde de sondages achetés (zéros si l'entreprise n'a jamais acheté). */
    CreditsModel credits(Long entrepriseId);

    /** Paiements REUSSI de l'entreprise, du plus récent au plus ancien (les intentions abandonnées n'y sont pas). */
    Page<PaiementModel> paiements(Long entrepriseId, int page, int taille);
}
