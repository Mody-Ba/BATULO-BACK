package com.batulo.service;

import com.batulo.service.model.ConversationModel;
import com.batulo.service.model.MessageInterneModel;
import org.springframework.data.domain.Page;

/**
 * Messagerie interne (AD-14, GE-07, SA-07 ; matrice des droits) : super admins <-> admin, admin <-> ses gestionnaires.
 * Un gestionnaire ne parle qu'à son admin ; jamais entre entreprises. Une conversation que l'on ne voit pas : 404.
 * Avec un abonnement non actif, la conversation admin <-> gestionnaire est en lecture seule ; celle avec Batulo
 * reste ouverte (c'est là que l'admin en a besoin).
 */
public interface MessagerieService {

    /**
     * Ouvre (ou retrouve) la conversation avec cet interlocuteur.
     *
     * @param interlocuteurId admin : un de ses gestionnaires, ou null pour Batulo ; gestionnaire : null (son admin) ;
     *                        super admin : un admin
     * @throws com.batulo.exception.ResourceNotFoundException INTERLOCUTEUR_INTROUVABLE
     */
    ConversationModel ouvrir(Long utilisateurId, Long interlocuteurId);

    /** Ses conversations, la plus récemment active d'abord. */
    Page<ConversationModel> conversations(Long utilisateurId, int page, int taille);

    /** Messages, les plus récents d'abord. */
    Page<MessageInterneModel> messages(Long utilisateurId, Long conversationId, int page, int taille);

    /** @throws com.batulo.exception.ActionInterditeException ABONNEMENT_EXPIRE (conversation avec un gestionnaire) */
    MessageInterneModel envoyer(Long utilisateurId, Long conversationId, String contenu);

    /** Marque lus les messages reçus de la conversation ; renvoie leur nombre. */
    int marquerLu(Long utilisateurId, Long conversationId);

    /** Total des messages non lus (pastille). */
    long nonLus(Long utilisateurId);
}
