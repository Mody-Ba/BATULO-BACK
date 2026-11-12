package com.batulo.service;

import com.batulo.entity.Conversation;
import com.batulo.entity.Message;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.CoteConversation;
import com.batulo.enums.TypeMessage;
import com.batulo.enums.TypeNotification;
import com.batulo.repository.ConversationRepository;
import com.batulo.repository.MessageRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publication des messages, partagée par la messagerie, l'envoi des statistiques et les demandes d'abonnement.
 * Composant non transactionnel : appelé depuis les transactions des services.
 */
@Component
public class Messagerie {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final NotificationService notificationService;

    public Messagerie(ConversationRepository conversationRepository, MessageRepository messageRepository,
                      NotificationService notificationService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.notificationService = notificationService;
    }

    /** La conversation de l'admin avec ce gestionnaire, ou avec Batulo si gestionnaire est null ; créée au besoin. */
    Conversation conversation(Utilisateur admin, Utilisateur gestionnaire) {
        return (gestionnaire == null
                ? conversationRepository.findByAdminIdAndGestionnaireIsNull(admin.getId())
                : conversationRepository.findByAdminIdAndGestionnaireId(admin.getId(), gestionnaire.getId()))
                .orElseGet(() -> {
                    Conversation conversation = new Conversation();
                    conversation.setAdmin(admin);
                    conversation.setGestionnaire(gestionnaire);
                    return conversationRepository.save(conversation);
                });
    }

    /**
     * Publie un message. Avec « notifier », le destinataire reçoit « Nouveau message » seulement si c'est son premier
     * message non lu de la conversation : une discussion ne crée pas une notification par message. Côté Batulo,
     * tous les super admins actifs sont notifiés.
     */
    Message poster(Conversation conversation, Utilisateur expediteur, CoteConversation cote, TypeMessage type,
                   Long referenceId, String contenu, boolean notifier) {
        CoteConversation destinataire = autreCote(conversation, cote);
        boolean premierNonLu = conversation.getId() == null
                || messageRepository.nonLus(conversation.getId(), destinataire) == 0;

        Message message = new Message();
        message.setConversation(conversation);
        message.setExpediteur(expediteur);
        message.setExpediteurNom(nom(expediteur) + (cote == CoteConversation.PLATEFORME ? " (Batulo)" : ""));
        message.setCote(cote);
        message.setType(type);
        message.setReferenceId(referenceId);
        message.setContenu(contenu);
        message = messageRepository.save(message);
        conversation.setDernierMessageLe(Instant.now());

        if (notifier && premierNonLu) {
            String texte = message.getExpediteurNom() + " vous a écrit.";
            switch (destinataire) {
                case PLATEFORME -> notificationService.notifierSuperAdmins(TypeNotification.NOUVEAU_MESSAGE,
                        "Nouveau message", texte);
                case ADMIN -> notificationService.notifier(conversation.getAdmin().getId(),
                        TypeNotification.NOUVEAU_MESSAGE, "Nouveau message", texte);
                case GESTIONNAIRE -> notificationService.notifier(conversation.getGestionnaire().getId(),
                        TypeNotification.NOUVEAU_MESSAGE, "Nouveau message", texte);
            }
        }
        return message;
    }

    /** L'autre côté de la conversation : admin <-> Batulo, ou admin <-> gestionnaire. */
    static CoteConversation autreCote(Conversation conversation, CoteConversation cote) {
        if (cote != CoteConversation.ADMIN) {
            return CoteConversation.ADMIN;
        }
        return conversation.getGestionnaire() == null ? CoteConversation.PLATEFORME : CoteConversation.GESTIONNAIRE;
    }

    static String nom(Utilisateur utilisateur) {
        return utilisateur.getPrenom() + " " + utilisateur.getNom();
    }
}
