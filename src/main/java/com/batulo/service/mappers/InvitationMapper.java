package com.batulo.service.mappers;

import com.batulo.entity.Invitation;
import com.batulo.service.RegleInvitation;
import com.batulo.service.model.InvitationModel;
import org.springframework.stereotype.Component;

/** Mapper DB : Invitation -> InvitationModel (jamais le sel ni le hash du jeton). */
@Component("dbInvitationMapper")
public class InvitationMapper {

    public InvitationModel toModel(Invitation i) {
        return new InvitationModel(i.getId(), i.getPrenom(), i.getNom(), i.getCourriel(), i.getTelephone(),
                i.getLangue(), RegleInvitation.statut(i), i.getEnvoiEnFileLe() != null, i.getDateCreation(),
                i.getPremierEnvoiLe(), i.getDernierEnvoiLe(), i.getDerniereRelanceLe(), i.getNombreEnvois(),
                i.getEchecEnvoiLe(), i.getOuverteLe(), i.getReponduLe(), i.getRevoqueeLe());
    }
}
