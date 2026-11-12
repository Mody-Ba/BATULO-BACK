package com.batulo.service;

import com.batulo.entity.Conversation;
import com.batulo.entity.Message;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.CoteConversation;
import com.batulo.enums.Role;
import com.batulo.enums.TypeMessage;
import com.batulo.exception.ActionInterditeException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.ConversationRepository;
import com.batulo.repository.MessageRepository;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.mappers.MessagerieMapper;
import com.batulo.service.model.ConversationModel;
import com.batulo.service.model.MessageInterneModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class MessagerieServiceImpl implements MessagerieService {

    private static final Logger log = LoggerFactory.getLogger(MessagerieServiceImpl.class);

    private final UtilisateurRepository utilisateurRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MessagerieMapper messagerieMapper;
    private final Messagerie messagerie;
    private final RegleCollecte regleCollecte;

    public MessagerieServiceImpl(UtilisateurRepository utilisateurRepository,
                                 ConversationRepository conversationRepository,
                                 MessageRepository messageRepository, MessagerieMapper messagerieMapper,
                                 Messagerie messagerie, RegleCollecte regleCollecte) {
        this.utilisateurRepository = utilisateurRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.messagerieMapper = messagerieMapper;
        this.messagerie = messagerie;
        this.regleCollecte = regleCollecte;
    }

    @Override
    @Transactional
    public ConversationModel ouvrir(Long utilisateurId, Long interlocuteurId) {
        Utilisateur acteur = acteur(utilisateurId);
        Conversation conversation = switch (acteur.getRole()) {
            case ADMIN -> messagerie.conversation(acteur, interlocuteurId == null ? null
                    : utilisateurRepository.findByIdAndEntrepriseIdAndRole(interlocuteurId,
                            acteur.getEntreprise().getId(), Role.GESTIONNAIRE).orElseThrow(this::introuvable));
            case GESTIONNAIRE -> {
                Utilisateur admin = utilisateurRepository.findFirstByEntrepriseIdAndRole(
                        acteur.getEntreprise().getId(), Role.ADMIN).orElseThrow(this::introuvable);
                if (interlocuteurId != null && !interlocuteurId.equals(admin.getId())) {
                    throw introuvable();
                }
                yield messagerie.conversation(admin, acteur);
            }
            case SUPER_ADMIN -> {
                if (interlocuteurId == null) {
                    throw introuvable();
                }
                Utilisateur admin = utilisateurRepository.findByIdAndRole(interlocuteurId, Role.ADMIN)
                        .orElseThrow(this::introuvable);
                yield messagerie.conversation(admin, null);
            }
        };
        return enModele(conversation, cote(acteur));
    }

    @Override
    public Page<ConversationModel> conversations(Long utilisateurId, int page, int taille) {
        Utilisateur acteur = acteur(utilisateurId);
        PageRequest pageRequest = PageRequest.of(page, taille,
                Sort.by(Sort.Order.desc("dernierMessageLe"), Sort.Order.desc("id")));
        Page<Conversation> conversations = switch (acteur.getRole()) {
            case ADMIN -> conversationRepository.findByAdminId(acteur.getId(), pageRequest);
            case GESTIONNAIRE -> conversationRepository.findByGestionnaireId(acteur.getId(), pageRequest);
            case SUPER_ADMIN -> conversationRepository.findByGestionnaireIsNull(pageRequest);
        };
        CoteConversation lecteur = cote(acteur);
        var ids = conversations.map(Conversation::getId).toList();
        Map<Long, Message> derniers = ids.isEmpty() ? Map.of() : messageRepository.derniersMessages(ids).stream()
                .collect(Collectors.toMap(m -> m.getConversation().getId(), Function.identity()));
        Map<Long, Long> nonLus = ids.isEmpty() ? Map.of() : messageRepository.nonLusParConversation(ids, lecteur).stream()
                .collect(Collectors.toMap(l -> (Long) l[0], l -> (Long) l[1]));
        return conversations.map(c -> messagerieMapper.toModel(c, lecteur, derniers.get(c.getId()),
                nonLus.getOrDefault(c.getId(), 0L)));
    }

    @Override
    public Page<MessageInterneModel> messages(Long utilisateurId, Long conversationId, int page, int taille) {
        Utilisateur acteur = acteur(utilisateurId);
        Conversation conversation = conversationVisible(acteur, conversationId);
        CoteConversation lecteur = cote(acteur);
        return messageRepository.findByConversationId(conversation.getId(), PageRequest.of(page, taille,
                        Sort.by(Sort.Order.desc("dateEnvoi"), Sort.Order.desc("id"))))
                .map(m -> messagerieMapper.toModel(m, lecteur));
    }

    @Override
    @Transactional
    public MessageInterneModel envoyer(Long utilisateurId, Long conversationId, String contenu) {
        Utilisateur acteur = acteur(utilisateurId);
        Conversation conversation = conversationVisible(acteur, conversationId);
        if (conversation.getGestionnaire() != null
                && !regleCollecte.entrepriseAutorisee(conversation.getAdmin().getEntreprise(), Instant.now())) {
            throw new ActionInterditeException("ABONNEMENT_EXPIRE", "L'abonnement de l'entreprise n'est pas actif : "
                    + "cette conversation est en lecture seule. L'admin peut toujours écrire à Batulo.");
        }
        CoteConversation cote = cote(acteur);
        Message message = messagerie.poster(conversation, acteur, cote, TypeMessage.TEXTE, null, contenu.trim(), true);
        log.info("Message {} envoyé dans la conversation {} par le compte {}", message.getId(), conversationId, utilisateurId);
        return messagerieMapper.toModel(message, cote);
    }

    @Override
    @Transactional
    public int marquerLu(Long utilisateurId, Long conversationId) {
        Utilisateur acteur = acteur(utilisateurId);
        Conversation conversation = conversationVisible(acteur, conversationId);
        return messageRepository.marquerLus(conversation.getId(), cote(acteur), Instant.now());
    }

    @Override
    public long nonLus(Long utilisateurId) {
        Utilisateur acteur = acteur(utilisateurId);
        return switch (acteur.getRole()) {
            case ADMIN -> messageRepository.nonLusAdmin(acteur.getId());
            case GESTIONNAIRE -> messageRepository.nonLusGestionnaire(acteur.getId());
            case SUPER_ADMIN -> messageRepository.nonLusPlateforme();
        };
    }

    /** Admin : ses conversations ; gestionnaire : la sienne ; super admin : celles avec Batulo. Sinon 404. */
    private Conversation conversationVisible(Utilisateur acteur, Long conversationId) {
        return conversationRepository.findWithParticipantsById(conversationId)
                .filter(c -> switch (acteur.getRole()) {
                    case ADMIN -> c.getAdmin().getId().equals(acteur.getId());
                    case GESTIONNAIRE -> c.getGestionnaire() != null && c.getGestionnaire().getId().equals(acteur.getId());
                    case SUPER_ADMIN -> c.getGestionnaire() == null;
                })
                .orElseThrow(() -> new ResourceNotFoundException("CONVERSATION_INTROUVABLE",
                        "Aucune conversation " + conversationId + " accessible."));
    }

    private ConversationModel enModele(Conversation conversation, CoteConversation lecteur) {
        Message dernier = conversation.getId() == null ? null
                : messageRepository.derniersMessages(List.of(conversation.getId())).stream().findFirst().orElse(null);
        long nonLus = messageRepository.nonLus(conversation.getId(), lecteur);
        return messagerieMapper.toModel(conversation, lecteur, dernier, nonLus);
    }

    private Utilisateur acteur(Long utilisateurId) {
        return utilisateurRepository.findWithEntrepriseById(utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("UTILISATEUR_INTROUVABLE", "Compte introuvable."));
    }

    private static CoteConversation cote(Utilisateur utilisateur) {
        return switch (utilisateur.getRole()) {
            case SUPER_ADMIN -> CoteConversation.PLATEFORME;
            case ADMIN -> CoteConversation.ADMIN;
            case GESTIONNAIRE -> CoteConversation.GESTIONNAIRE;
        };
    }

    private ResourceNotFoundException introuvable() {
        return new ResourceNotFoundException("INTERLOCUTEUR_INTROUVABLE",
                "Vous ne pouvez pas écrire à ce compte (gestionnaire de votre entreprise, votre admin, ou Batulo).");
    }
}
