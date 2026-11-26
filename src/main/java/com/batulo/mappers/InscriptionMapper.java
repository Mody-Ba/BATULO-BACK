package com.batulo.mappers;

import com.batulo.enums.OffreInscription;
import com.batulo.service.model.InscriptionEffectueeModel;
import com.batulo.service.model.InscriptionModel;
import com.batulo.service.model.InscriptionRequest;
import com.batulo.service.model.InscriptionResponse;
import org.springframework.stereotype.Component;

/**
 * Mapper API : convertit uniquement Request -> Model et Model -> Response.
 * Ne doit jamais connaître les entités JPA.
 */
@Component("apiInscriptionMapper")
public class InscriptionMapper {

    private final AuthMapper authMapper;

    public InscriptionMapper(AuthMapper authMapper) {
        this.authMapper = authMapper;
    }

    public InscriptionModel toModel(InscriptionRequest req) {
        return new InscriptionModel(
                req.prenom(),
                req.nom(),
                req.courriel(),
                req.motDePasse(),
                req.confirmationMotDePasse(),
                req.nomEntreprise(),
                req.offre(),
                req.nombreSondages());
    }

    public InscriptionResponse toResponse(InscriptionEffectueeModel model) {
        String message = model.offre() == OffreInscription.ACHAT_SONDAGES
                ? "Inscription réussie. Payez vos " + model.nombreSondages() + " sondage(s) pour commencer."
                : model.offre() != null
                ? "Inscription réussie. Payez votre abonnement pour commencer."
                : "Inscription réussie. Votre compte est en attente de paiement : abonnez-vous ou achetez des sondages.";
        return new InscriptionResponse(
                message,
                authMapper.toResponse(model.utilisateur()),
                model.abonnementId(),
                model.statutAbonnement(),
                model.offre(),
                model.nombreSondages());
    }
}
