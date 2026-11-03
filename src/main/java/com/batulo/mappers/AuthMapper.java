package com.batulo.mappers;

import com.batulo.service.model.UtilisateurModel;
import com.batulo.service.model.UtilisateurResponse;
import org.springframework.stereotype.Component;

/**
 * Mapper API : convertit uniquement Request -> Model et Model -> Response.
 * Ne doit jamais connaître l'entité JPA Utilisateur.
 * (Connexion, jetons et mots de passe ajoutés à l'étape 7.)
 */
@Component("apiAuthMapper")
public class AuthMapper {

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
                model.peutCreerSondages(),
                model.dateCreation());
    }
}
