package com.batulo.service.mappers;

import com.batulo.entity.Conversation;
import com.batulo.entity.Message;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.CoteConversation;
import com.batulo.enums.Role;
import com.batulo.service.model.ConversationModel;
import com.batulo.service.model.MessageInterneModel;
import org.springframework.stereotype.Component;

/**
 * Mapper DB : convertit uniquement les entités Conversation et Message en modèles, vus d'un côté (celui du lecteur).
 * Ne doit jamais connaître les Response.
 */
@Component("dbMessagerieMapper")
public class MessagerieMapper {

    private static final int LONGUEUR_EXTRAIT = 100;

    public MessageInterneModel toModel(Message entity, CoteConversation lecteur) {
        return new MessageInterneModel(
                entity.getId(),
                entity.getConversation().getId(),
                entity.getType(),
                entity.getReferenceId(),
                entity.getContenu(),
                entity.getExpediteur() == null ? null : entity.getExpediteur().getId(),
                entity.getExpediteurNom(),
                entity.getCote(),
                entity.getCote() != lecteur,
                entity.getDateEnvoi(),
                entity.getLuLe());
    }

    /** @param dernier dernier message de la conversation, ou null */
    public ConversationModel toModel(Conversation entity, CoteConversation lecteur, Message dernier, long nonLus) {
        return new ConversationModel(
                entity.getId(),
                interlocuteur(entity, lecteur),
                dernier == null ? null : new ConversationModel.DernierMessage(extrait(dernier.getContenu()),
                        dernier.getDateEnvoi(), dernier.getExpediteurNom(), dernier.getType(), dernier.getCote() != lecteur),
                nonLus,
                entity.getDernierMessageLe());
    }

    private static ConversationModel.Interlocuteur interlocuteur(Conversation conversation, CoteConversation lecteur) {
        if (lecteur == CoteConversation.ADMIN) {
            Utilisateur gestionnaire = conversation.getGestionnaire();
            return gestionnaire == null
                    ? new ConversationModel.Interlocuteur(null, "Batulo", "", Role.SUPER_ADMIN, null)
                    : personne(gestionnaire, conversation);
        }
        return personne(conversation.getAdmin(), conversation);
    }

    private static ConversationModel.Interlocuteur personne(Utilisateur utilisateur, Conversation conversation) {
        return new ConversationModel.Interlocuteur(utilisateur.getId(), utilisateur.getPrenom(), utilisateur.getNom(),
                utilisateur.getRole(), conversation.getAdmin().getEntreprise().getNom());
    }

    private static String extrait(String contenu) {
        String ligne = contenu.replaceAll("\\s+", " ").trim();
        return ligne.length() <= LONGUEUR_EXTRAIT ? ligne : ligne.substring(0, LONGUEUR_EXTRAIT - 1) + "…";
    }
}
