package com.batulo.mappers;

import com.batulo.service.model.ApparenceModel;
import com.batulo.service.model.ApparenceResponse;
import com.batulo.service.model.LogoInfoModel;
import com.batulo.service.model.LogoInfoResponse;
import com.batulo.service.model.PersonnalisationInfoModel;
import com.batulo.service.model.PersonnalisationModel;
import com.batulo.service.model.PersonnalisationRequest;
import com.batulo.service.model.PersonnalisationResponse;
import org.springframework.stereotype.Component;

/**
 * Mapper API de la personnalisation : Request -> Model et Model -> Response. Partagé avec le formulaire public,
 * l'aperçu et la fiche super admin. Ne connaît pas les entités.
 */
@Component("apiPersonnalisationMapper")
public class PersonnalisationMapper {

    public PersonnalisationInfoModel toModel(PersonnalisationRequest req) {
        return new PersonnalisationInfoModel(req.nomAffiche(), req.couleurPrincipale());
    }

    public PersonnalisationResponse toResponse(PersonnalisationModel model) {
        return model == null ? null : new PersonnalisationResponse(model.nomEntreprise(), model.nomAffiche(),
                model.nomEffectif(), model.couleurPrincipale(), model.couleurEffective(), model.couleurTexte(),
                toResponse(model.logo()));
    }

    public ApparenceResponse toResponse(ApparenceModel model) {
        return model == null ? null : new ApparenceResponse(model.nomAffiche(), model.logoUrl(),
                model.couleurPrincipale(), model.couleurTexte());
    }

    private LogoInfoResponse toResponse(LogoInfoModel model) {
        return model == null ? null : new LogoInfoResponse(model.url(), model.typeMime(), model.taille(),
                model.largeur(), model.hauteur(), model.dateModification());
    }
}
