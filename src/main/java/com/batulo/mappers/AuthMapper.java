package com.batulo.mappers;

import com.batulo.service.model.ChangementMotDePasseModel;
import com.batulo.service.model.ChangementMotDePasseRequest;
import com.batulo.service.model.ConnexionModel;
import com.batulo.service.model.ConnexionRequest;
import com.batulo.service.model.ConnexionResponse;
import com.batulo.service.model.IdentifiantsModel;
import com.batulo.service.model.ProfilModel;
import com.batulo.service.model.ProfilResponse;
import com.batulo.service.model.ReinitialisationMotDePasseModel;
import com.batulo.service.model.ReinitialisationMotDePasseRequest;
import com.batulo.service.model.UtilisateurModel;
import com.batulo.service.model.UtilisateurResponse;
import org.springframework.stereotype.Component;

/**
 * Mapper API : convertit uniquement Request -> Model et Model -> Response.
 * Ne doit jamais connaître l'entité JPA Utilisateur.
 */
@Component("apiAuthMapper")
public class AuthMapper {

    private static final String TYPE_JETON = "Bearer";

    public IdentifiantsModel toModel(ConnexionRequest req) {
        return new IdentifiantsModel(req.courriel(), req.motDePasse());
    }

    public ReinitialisationMotDePasseModel toModel(ReinitialisationMotDePasseRequest req) {
        return new ReinitialisationMotDePasseModel(req.jeton(), req.nouveauMotDePasse(), req.confirmationMotDePasse());
    }

    public ChangementMotDePasseModel toModel(ChangementMotDePasseRequest req) {
        return new ChangementMotDePasseModel(req.motDePasseActuel(), req.nouveauMotDePasse(),
                req.confirmationMotDePasse());
    }

    public ConnexionResponse toResponse(ConnexionModel model) {
        return new ConnexionResponse(
                model.jeton(),
                TYPE_JETON,
                model.expireDansSecondes(),
                model.jetonRafraichissement(),
                model.utilisateur().role(),
                model.statutAbonnement(),
                model.utilisateur().motDePasseTemporaire(),
                toResponse(model.utilisateur()));
    }

    public ProfilResponse toResponse(ProfilModel model) {
        return new ProfilResponse(toResponse(model.utilisateur()), model.statutAbonnement());
    }

    public UtilisateurResponse toResponse(UtilisateurModel model) {
        return new UtilisateurResponse(
                model.id(),
                model.prenom(),
                model.nom(),
                model.courriel(),
                model.role(),
                model.entrepriseId(),
                model.entrepriseNom(),
                model.actif(),
                model.motDePasseTemporaire(),
                model.motDePasseTemporaireExpireLe(),
                model.peutCreerSondages(),
                model.dateCreation());
    }
}
