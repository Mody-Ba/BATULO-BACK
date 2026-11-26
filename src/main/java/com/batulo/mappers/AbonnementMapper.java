package com.batulo.mappers;

import com.batulo.service.model.DemandeAchatSondagesModel;
import com.batulo.service.model.CreditsResponse;
import com.batulo.service.model.CreditsModel;
import com.batulo.service.model.AchatSondagesRequest;
import com.batulo.service.model.AchatSondagesMobileRequest;
import com.batulo.enums.TypePaiement;
import com.batulo.service.model.AbonnementModel;
import com.batulo.service.model.AbonnementResponse;
import com.batulo.service.model.CarteTestResponse;
import com.batulo.service.model.ConfirmationPaiementModel;
import com.batulo.service.model.DemandePaiementMobileModel;
import com.batulo.service.model.PaiementMobileModel;
import com.batulo.service.model.PaiementMobileRequest;
import com.batulo.service.model.PaiementMobileResponse;
import com.batulo.service.model.SimulationWebhookMoosylModel;
import com.batulo.service.model.SimulationWebhookMoosylResponse;
import com.batulo.service.model.StatutPaiementMobileModel;
import com.batulo.service.model.StatutPaiementMobileResponse;
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
        return new FormuleResponse(model.formule(), model.libelle(), model.prix(), model.devise(), model.duree(),
                model.prixMobile(), model.deviseMobile(), model.type(), model.quantiteMin(), model.quantiteMax());
    }

    public DemandeAchatSondagesModel toModel(AchatSondagesRequest req) {
        return new DemandeAchatSondagesModel(req.quantite(), null);
    }

    public DemandeAchatSondagesModel toModel(AchatSondagesMobileRequest req) {
        return new DemandeAchatSondagesModel(req.quantite(), req.telephone());
    }

    public CreditsResponse toResponse(CreditsModel model) {
        return model == null ? null
                : new CreditsResponse(model.solde(), model.totalAchete(), model.totalUtilise(), model.compteCredits());
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
                model.devise(),
                model.offreChoisie(),
                model.nombreSondagesChoisi(),
                toResponse(model.credits()));
    }

    public PageResponse<PaiementResponse> toPaiementResponses(Page<PaiementModel> page) {
        return new PageResponse<>(
                page.getContent().stream().map(model -> new PaiementResponse(model.id(), model.datePaiement(),
                        model.formule(), model.montant(), model.devise(), model.fournisseur(), model.type(),
                        model.quantite())).toList(),
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
                model.devise(),
                model.type(),
                model.quantite());
    }

    public PaiementConfirmeResponse toResponse(PaiementConfirmeModel model) {
        boolean achat = model.type() == TypePaiement.ACHAT_SONDAGES;
        String message;
        if (model.dejaConfirme()) {
            message = achat ? "Ce paiement avait déjà été confirmé : les sondages ne sont pas crédités une seconde fois."
                    : "Ce paiement avait déjà été confirmé : l'abonnement n'est pas prolongé une seconde fois.";
        } else {
            message = achat ? "Paiement confirmé : " + model.quantite() + " sondage(s) ajouté(s) à votre solde."
                    : "Paiement confirmé : votre abonnement est à jour.";
        }
        return new PaiementConfirmeResponse(message, toResponse(model.abonnement()), model.type(), model.quantite(),
                toResponse(model.credits()));
    }

    public DemandePaiementMobileModel toModel(PaiementMobileRequest req) {
        return new DemandePaiementMobileModel(req.formule(), req.telephone());
    }

    public PaiementMobileResponse toResponse(PaiementMobileModel model) {
        return new PaiementMobileResponse(model.reference(), model.checkoutUrl(), model.formule(), model.montant(),
                model.devise(), model.expireLe(), model.type(), model.quantite());
    }

    public StatutPaiementMobileResponse toResponse(StatutPaiementMobileModel model) {
        return new StatutPaiementMobileResponse(model.reference(), model.statut(), model.formule(), model.montant(),
                model.devise(), model.dateCreation(), model.datePaiement(), toResponse(model.abonnement()), model.type(),
                model.quantite(), toResponse(model.credits()));
    }

    public SimulationWebhookMoosylResponse toResponse(SimulationWebhookMoosylModel model) {
        return new SimulationWebhookMoosylResponse(model.reference(), model.evenement(), model.statutEnvoye(),
                model.statutHttp(), model.reponse(), model.statutPaiement());
    }

    public CarteTestResponse toCarteTestResponse(IntentionStripeModel model) {
        String message = "succeeded".equals(model.statut())
                ? "Paiement de test accepté par Stripe. Appelez maintenant POST /api/abonnements/paiement/confirmer."
                : "Stripe n'a pas accepté le paiement de test (statut : " + model.statut() + ").";
        return new CarteTestResponse(model.id(), model.statut(), message);
    }
}
