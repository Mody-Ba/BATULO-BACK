package com.batulo.mappers;

import com.batulo.service.model.AbonnementModel;
import com.batulo.service.model.AbonnementResponse;
import com.batulo.service.model.CarteTestResponse;
import com.batulo.service.model.ConfirmationPaiementModel;
import com.batulo.service.model.ConfirmationPaiementRequest;
import com.batulo.service.model.DemandeIntentionPaiementModel;
import com.batulo.service.model.FormuleModel;
import com.batulo.service.model.FormuleResponse;
import com.batulo.service.model.IntentionPaiementModel;
import com.batulo.service.model.IntentionPaiementRequest;
import com.batulo.service.model.IntentionPaiementResponse;
import com.batulo.service.model.IntentionStripeModel;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.PaiementConfirmeModel;
import com.batulo.service.model.PaiementConfirmeResponse;
import com.batulo.service.model.PaiementModel;
import com.batulo.service.model.PaiementResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

/**
 * Mapper API : convertit uniquement Request -> Model et Model -> Response.
 * Ne doit jamais connaître les entités JPA ni la bibliothèque Stripe.
 */
@Component("apiAbonnementMapper")
public class AbonnementMapper {

    public DemandeIntentionPaiementModel toModel(IntentionPaiementRequest req) {
        return new DemandeIntentionPaiementModel(req.formule());
    }

    public ConfirmationPaiementModel toModel(ConfirmationPaiementRequest req) {
        return new ConfirmationPaiementModel(req.paymentIntentId().trim());
    }

    public FormuleResponse toResponse(FormuleModel model) {
        return new FormuleResponse(model.formule(), model.libelle(), model.prix(), model.devise(), model.duree());
    }

    public AbonnementResponse toResponse(AbonnementModel model) {
        return new AbonnementResponse(
                model.id(),
                model.entrepriseId(),
                model.entrepriseNom(),
                model.formule(),
                model.statut(),
                model.dateDebut(),
                model.dateExpiration(),
                model.joursRestants(),
                model.montant(),
                model.devise());
    }

    public PageResponse<PaiementResponse> toPaiementResponses(Page<PaiementModel> page) {
        return new PageResponse<>(
                page.getContent().stream().map(model -> new PaiementResponse(model.id(), model.datePaiement(),
                        model.formule(), model.montant(), model.devise())).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public IntentionPaiementResponse toResponse(IntentionPaiementModel model) {
        return new IntentionPaiementResponse(
                model.paymentIntentId(),
                model.clientSecret(),
                model.clePublique(),
                model.formule(),
                model.montant(),
                model.devise());
    }

    public PaiementConfirmeResponse toResponse(PaiementConfirmeModel model) {
        String message = model.dejaConfirme()
                ? "Ce paiement avait déjà été confirmé : l'abonnement n'est pas prolongé une seconde fois."
                : "Paiement confirmé : votre abonnement est à jour.";
        return new PaiementConfirmeResponse(message, toResponse(model.abonnement()));
    }

    public CarteTestResponse toCarteTestResponse(IntentionStripeModel model) {
        String message = "succeeded".equals(model.statut())
                ? "Paiement de test accepté par Stripe. Appelez maintenant POST /api/abonnements/paiement/confirmer."
                : "Stripe n'a pas accepté le paiement de test (statut : " + model.statut() + ").";
        return new CarteTestResponse(model.id(), model.statut(), message);
    }
}
