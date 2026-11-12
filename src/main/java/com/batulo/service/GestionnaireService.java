package com.batulo.service;

import com.batulo.service.model.CreationGestionnaireModel;
import com.batulo.service.model.ModificationGestionnaireModel;
import com.batulo.service.model.UtilisateurModel;
import org.springframework.data.domain.Page;

/**
 * Gestion des gestionnaires par l'admin d'une entreprise (AD-07 à AD-10). Toutes les opérations sont limitées
 * à l'entreprise de l'admin : un gestionnaire d'une autre entreprise donne ResourceNotFoundException (404).
 * <p>
 * Les règles d'abonnement (EN_ATTENTE_PAIEMENT ou DESACTIVE : tout est refusé ; EXPIRE : lecture seule) sont
 * appliquées avant d'arriver ici, par AccesAbonnementInterceptor.
 */
public interface GestionnaireService {

    /**
     * Crée un gestionnaire actif, sans permission de créer des sondages, avec un mot de passe temporaire de 24 h.
     *
     * @throws com.batulo.exception.CompteExistantException courriel déjà utilisé sur la plateforme
     */
    UtilisateurModel creer(Long adminId, CreationGestionnaireModel creation);

    /**
     * Gestionnaires de l'entreprise, du plus récent au plus ancien.
     *
     * @param recherche facultatif : prénom, nom ou courriel contenant ce texte (sans majuscules ni accents)
     * @param actif     facultatif : actifs (true) ou désactivés (false) seulement
     */
    Page<UtilisateurModel> lister(Long adminId, String recherche, Boolean actif, int page, int taille);

    UtilisateurModel detail(Long adminId, Long gestionnaireId);

    /** @throws com.batulo.exception.CompteExistantException courriel déjà utilisé par un autre compte */
    UtilisateurModel modifier(Long adminId, Long gestionnaireId, ModificationGestionnaireModel modification);

    void supprimer(Long adminId, Long gestionnaireId);

    UtilisateurModel changerActivation(Long adminId, Long gestionnaireId, boolean actif);

    UtilisateurModel changerPermissionSondages(Long adminId, Long gestionnaireId, boolean peutCreerSondages);

    /** Nouveau mot de passe temporaire (24 h) ; l'ancien ne fonctionne plus. */
    UtilisateurModel renvoyerMotDePasseTemporaire(Long adminId, Long gestionnaireId);
}
