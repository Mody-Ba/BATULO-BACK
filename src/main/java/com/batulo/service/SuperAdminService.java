package com.batulo.service;

import com.batulo.enums.StatutAbonnement;
import com.batulo.service.model.AbonnementAdminModel;
import com.batulo.service.model.AdminDetailModel;
import com.batulo.service.model.CreationAdminModel;
import com.batulo.service.model.ModificationAdminModel;
import com.batulo.service.model.MotDePasseTemporaireModel;
import com.batulo.service.model.UtilisateurModel;
import org.springframework.data.domain.Page;

/**
 * Espace du super admin (SA-01 à SA-06) : comptes admin et suivi des abonnements.
 * Le super admin lui-même n'est jamais modifiable par ces opérations (ActionInterditeException, 403).
 * Les admins introuvables, ou les identifiants qui désignent un gestionnaire, donnent ResourceNotFoundException.
 */
public interface SuperAdminService {

    /**
     * Crée l'entreprise, l'admin avec un mot de passe temporaire valide 24 h et un abonnement EN_ATTENTE_PAIEMENT.
     *
     * @throws com.batulo.exception.CompteExistantException courriel déjà utilisé
     */
    AdminDetailModel creerAdmin(CreationAdminModel creation);

    /** Admins du plus récent au plus ancien. */
    Page<UtilisateurModel> listerAdmins(int page, int taille);

    AdminDetailModel detailAdmin(Long adminId);

    /** @throws com.batulo.exception.CompteExistantException courriel déjà utilisé par un autre compte */
    AdminDetailModel modifierAdmin(Long adminId, ModificationAdminModel modification);

    /** Supprime l'admin, son entreprise, ses gestionnaires, son abonnement et ses paiements. */
    void supprimerAdmin(Long adminId);

    /** Désactiver : entreprise et admin bloqués, abonnement DESACTIVE. Activer : statut recalculé selon les dates. */
    AdminDetailModel changerActivation(Long adminId, boolean actif);

    /** Génère un nouveau mot de passe temporaire (24 h) ; l'ancien mot de passe ne fonctionne plus. */
    MotDePasseTemporaireModel renvoyerMotDePasseTemporaire(Long adminId);

    /**
     * Abonnements avec leur admin, filtrés par statut effectif (null : tous), ceux qui expirent le plus tôt en tête.
     */
    Page<AbonnementAdminModel> listerAbonnements(StatutAbonnement statut, int page, int taille);
}
