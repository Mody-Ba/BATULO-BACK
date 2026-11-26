package com.batulo.service;

import com.batulo.service.model.DemandeAchatSondagesModel;
import com.batulo.service.model.DemandePaiementMobileModel;
import com.batulo.service.model.PaiementMobileModel;
import com.batulo.service.model.StatutPaiementMobileModel;

/**
 * Paiement mobile de l'abonnement par Moosyl (Bankily, Sedad, Masrivi...), en MRU. Le webhook est la source
 * principale du statut ; la lecture du statut relit aussi la session chez Moosyl tant que le paiement est en
 * attente. Les deux activent l'abonnement par ActivationPaiement : un paiement ne compte qu'une fois (RG-09).
 */
public interface PaiementMobileService {

    /** Résultat du traitement d'un webhook, pour la réponse HTTP renvoyée à Moosyl. */
    enum ResultatWebhook {
        /** Paiement mis à jour (ou déjà à jour) : 200. */
        TRAITE,
        /** Événement sans effet (autre événement, statut en cours, référence inconnue, montant incohérent) : 200. */
        IGNORE,
        /** Signature absente ou fausse : 401 (Moosyl ne réessaie pas un 4xx). */
        SIGNATURE_INVALIDE,
        /** Corps illisible malgré une signature valide : 400. */
        CORPS_INVALIDE
    }

    /**
     * Crée le paiement EN_ATTENTE et la session Moosyl ; l'admin est redirigé vers checkoutUrl, puis revient sur
     * l'URL de retour du frontend avec ?reference=.
     *
     * @throws com.batulo.exception.RegleGestionException ENTREPRISE_DESACTIVEE
     * @throws com.batulo.exception.MoosylNonConfigureException clé Moosyl absente (503)
     * @throws com.batulo.exception.MoosylIndisponibleException refus ou panne de Moosyl (502)
     */
    PaiementMobileModel demarrer(Long entrepriseId, DemandePaiementMobileModel demande);

    /**
     * Achat de sondages par paiement mobile (quantité × prix unitaire en MRU) : même parcours que {@link #demarrer} ;
     * le solde est crédité une seule fois quand Moosyl confirme (webhook ou relecture du statut).
     *
     * @throws com.batulo.exception.RegleGestionException QUANTITE_INVALIDE (hors de 1 à 1000), ENTREPRISE_DESACTIVEE
     */
    PaiementMobileModel demarrerAchat(Long entrepriseId, DemandeAchatSondagesModel demande);

    /**
     * Statut d'un paiement de l'entreprise. EN_ATTENTE : relit la session chez Moosyl et active l'abonnement si elle
     * est payée (idempotent avec le webhook).
     *
     * @throws com.batulo.exception.ResourceNotFoundException PAIEMENT_INTROUVABLE (autre entreprise comprise)
     */
    StatutPaiementMobileModel statut(Long entrepriseId, String reference);

    /** Webhook Moosyl : signature vérifiée sur le corps brut, puis paiement mis à jour. */
    ResultatWebhook traiterWebhook(byte[] corps, String signature, String idLivraison);
}
