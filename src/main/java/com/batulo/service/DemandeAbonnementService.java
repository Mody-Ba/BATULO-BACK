package com.batulo.service;

import com.batulo.enums.Formule;
import com.batulo.enums.StatutDemande;
import com.batulo.service.model.DemandeAbonnementModel;
import org.springframework.data.domain.Page;

/**
 * Demandes d'augmentation d'abonnement (AD-05, SA-07), échangées dans la conversation admin <-> Batulo.
 * Purement informatives : aucune ne modifie l'abonnement (RG-09) ; acceptée, l'admin paie lui-même la formule.
 */
public interface DemandeAbonnementService {

    /** @throws com.batulo.exception.ConflitException DEMANDE_EN_COURS : une demande attend déjà une réponse */
    DemandeAbonnementModel demander(Long adminId, Formule formuleSouhaitee, String message);

    /** Admin : ses demandes, les plus récentes d'abord. */
    Page<DemandeAbonnementModel> mesDemandes(Long adminId, int page, int taille);

    /** Super admin : toutes les demandes (statut null : toutes), les plus récentes d'abord. */
    Page<DemandeAbonnementModel> lister(StatutDemande statut, int page, int taille);

    /**
     * Super admin : accepte (TRAITEE) ou refuse (REFUSEE) une demande en attente, avec une réponse.
     *
     * @throws com.batulo.exception.ConflitException DEMANDE_DEJA_TRAITEE
     * @throws com.batulo.exception.RegleGestionException STATUT_INVALIDE (EN_ATTENTE)
     */
    DemandeAbonnementModel traiter(Long superAdminId, Long demandeId, StatutDemande statut, String reponse);
}
