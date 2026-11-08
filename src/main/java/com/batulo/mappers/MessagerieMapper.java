package com.batulo.mappers;

import com.batulo.service.model.ConversationModel;
import com.batulo.service.model.ConversationResponse;
import com.batulo.service.model.DemandeAbonnementModel;
import com.batulo.service.model.DemandeAbonnementResponse;
import com.batulo.service.model.EnvoiStatistiqueDemandeModel;
import com.batulo.service.model.EnvoiStatistiqueModel;
import com.batulo.service.model.EnvoiStatistiqueRequest;
import com.batulo.service.model.EnvoiStatistiqueResponse;
import com.batulo.service.model.MessageInterneModel;
import com.batulo.service.model.MessageInterneResponse;
import com.batulo.service.model.NombreNonLuesResponse;
import com.batulo.service.model.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.function.Function;

/**
 * Mapper API de la messagerie, des envois de statistiques et des demandes d'abonnement : Request -> Model et
 * Model -> Response. Ne doit jamais connaître les entités JPA.
 */
@Component("apiMessagerieMapper")
public class MessagerieMapper {

    private final StatistiqueMapper statistiqueMapper;

    public MessagerieMapper(StatistiqueMapper statistiqueMapper) {
        this.statistiqueMapper = statistiqueMapper;
    }

    public ConversationResponse toResponse(ConversationModel m) {
        ConversationModel.Interlocuteur i = m.interlocuteur();
        ConversationModel.DernierMessage d = m.dernierMessage();
        return new ConversationResponse(m.id(),
                new ConversationResponse.Interlocuteur(i.id(), i.prenom(), i.nom(), i.role(), i.entreprise()),
                d == null ? null : new ConversationResponse.DernierMessage(d.extrait(), d.dateEnvoi(), d.expediteurNom(),
                        d.type(), d.recu()),
                m.nonLus(), m.dernierMessageLe());
    }

    public MessageInterneResponse toResponse(MessageInterneModel m) {
        return new MessageInterneResponse(m.id(), m.conversationId(), m.type(), m.referenceId(), m.contenu(),
                m.expediteurId(), m.expediteurNom(), m.cote(), m.recu(), m.dateEnvoi(), m.luLe());
    }

    public EnvoiStatistiqueDemandeModel toModel(EnvoiStatistiqueRequest r) {
        return new EnvoiStatistiqueDemandeModel(r.commentaire(), Boolean.TRUE.equals(r.lignee()), r.du(), r.au(),
                statistiqueMapper.toCriteres(r.critere()), r.granulariteDate());
    }

    public EnvoiStatistiqueResponse toResponse(EnvoiStatistiqueModel m) {
        return new EnvoiStatistiqueResponse(m.id(), m.sondageId(), m.sondageTitre(), m.version(), m.lignee(), m.du(),
                m.au(), m.granulariteDate(), m.criteres(), m.gestionnaireId(), m.expediteurNom(), m.commentaire(),
                m.dateEnvoi(), m.consulteLe());
    }

    public DemandeAbonnementResponse toResponse(DemandeAbonnementModel m) {
        return new DemandeAbonnementResponse(m.id(), m.entrepriseId(), m.entreprise(), m.adminId(), m.adminNom(),
                m.adminCourriel(), m.formuleSouhaitee(), m.formuleActuelle(), m.statutAbonnement(), m.message(),
                m.statut(), m.reponse(), m.traiteeParNom(), m.dateDemande(), m.dateTraitement());
    }

    public NombreNonLuesResponse toNonLus(long nombre) {
        return new NombreNonLuesResponse(nombre);
    }

    public <M, R> PageResponse<R> toResponse(Page<M> page, Function<M, R> conversion) {
        return new PageResponse<>(page.getContent().stream().map(conversion).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

}
