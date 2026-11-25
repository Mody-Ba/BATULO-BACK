package com.batulo.mappers;

import com.batulo.service.Langues;
import com.batulo.service.model.InvitationInfoModel;
import com.batulo.service.model.InvitationModel;
import com.batulo.service.model.InvitationRequest;
import com.batulo.service.model.InvitationResponse;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.RapportEnvoiModel;
import com.batulo.service.model.RapportEnvoiResponse;
import com.batulo.service.model.RapportImportModel;
import com.batulo.service.model.RapportImportResponse;
import com.batulo.service.model.ResumeInvitationsModel;
import com.batulo.service.model.ResumeInvitationsResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/** Mapper API des invitations : Request -> Model et Model -> Response, sans entité. */
@Component("apiInvitationMapper")
public class InvitationMapper {

    public InvitationInfoModel toModel(InvitationRequest r) {
        return new InvitationInfoModel(r.prenom(), r.nom(), r.courriel(), r.telephone(), Langues.lire(r.langue()));
    }

    public InvitationResponse toResponse(InvitationModel m) {
        return new InvitationResponse(m.id(), m.prenom(), m.nom(), m.courriel(), m.telephone(), m.langue(), m.statut(),
                m.envoiEnCours(), m.dateCreation(), m.premierEnvoiLe(), m.dernierEnvoiLe(), m.derniereRelanceLe(),
                m.nombreEnvois(), m.echecEnvoiLe(), m.ouverteLe(), m.reponduLe(), m.revoqueeLe());
    }

    public PageResponse<InvitationResponse> toResponse(Page<InvitationModel> page) {
        return new PageResponse<>(page.getContent().stream().map(this::toResponse).toList(), page.getNumber(),
                page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    public ResumeInvitationsResponse toResponse(ResumeInvitationsModel m) {
        return new ResumeInvitationsResponse(m.total(), m.aInviter(), m.invitees(), m.ouvertes(), m.repondues(),
                m.revoquees(), m.enEchec(), m.envoyees(), m.tauxReponse());
    }

    public RapportImportResponse toResponse(RapportImportModel m) {
        return new RapportImportResponse(m.acceptees(), m.refusees().stream()
                .map(l -> new RapportImportResponse.LigneRefusee(l.ligne(), l.courriel(), l.raison(), l.message()))
                .toList());
    }

    public RapportEnvoiResponse toResponse(RapportEnvoiModel m) {
        return new RapportEnvoiResponse(m.enFile(), m.ignorees().stream()
                .map(i -> new RapportEnvoiResponse.Ignoree(i.invitationId(), i.raison())).toList());
    }
}
