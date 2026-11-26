package com.batulo.controller;

import com.batulo.service.model.CreditsResponse;
import com.batulo.service.model.AchatSondagesRequest;
import com.batulo.service.model.AchatSondagesMobileRequest;
import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.AbonnementMapper;
import com.batulo.service.AbonnementService;
import com.batulo.service.PaiementMobileService;
import com.batulo.service.model.AbonnementResponse;
import com.batulo.service.model.ConfirmationPaiementRequest;
import com.batulo.service.model.FormuleResponse;
import com.batulo.service.model.IntentionPaiementRequest;
import com.batulo.service.model.IntentionPaiementResponse;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.PaiementMobileRequest;
import com.batulo.service.model.PaiementMobileResponse;
import com.batulo.service.model.StatutPaiementMobileResponse;
import com.batulo.service.model.PaiementConfirmeResponse;
import com.batulo.service.model.PaiementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Formules, abonnement de l'admin et paiement Stripe (mode TEST). Il n'existe volontairement aucune route
 * d'annulation ni d'activation manuelle : seul un paiement revérifié auprès de Stripe active un abonnement.
 * Formules : publiques. Le reste : rôle ADMIN, entreprise de l'admin du jeton ; permis même si l'abonnement est
 * en attente de paiement ou expiré, pour pouvoir payer ou renouveler.
 */
@RestController
@RequestMapping("/api/abonnements")
@Tag(name = "Abonnement", description = "Formules, abonnement de l'admin, achat de sondages et paiement (Stripe en "
        + "mode test, Moosyl)")
public class AbonnementController {

    private final AbonnementService abonnementService;
    private final AbonnementMapper abonnementMapper;
    private final PaiementMobileService paiementMobileService;

    public AbonnementController(AbonnementService abonnementService, AbonnementMapper abonnementMapper,
                                PaiementMobileService paiementMobileService) {
        this.abonnementService = abonnementService;
        this.abonnementMapper = abonnementMapper;
        this.paiementMobileService = paiementMobileService;
    }

    @GetMapping("/formules")
    @SecurityRequirements
    @Operation(summary = "Formules disponibles et prix (public)",
            description = "MENSUEL et ANNUEL (type ABONNEMENT, publication illimitée pendant la période), puis un 3e "
                    + "élément type ACHAT_SONDAGES (formule null) : prix et prixMobile d'UN sondage, quantiteMin 1, "
                    + "quantiteMax 1000. Montant d'un achat = quantité × prix unitaire.")
    public List<FormuleResponse> formules() {
        return abonnementService.formules().stream().map(abonnementMapper::toResponse).toList();
    }

    @GetMapping("/moi")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mon abonnement : formule, statut, date d'expiration, jours restants",
            description = "Le statut est recalculé à partir des dates. 404 si l'entreprise n'a pas d'abonnement.")
    public AbonnementResponse monAbonnement(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi) {
        return abonnementMapper.toResponse(abonnementService.monAbonnement(moi.entrepriseId()));
    }

    @GetMapping("/credits")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mes sondages achetés : solde, total acheté, total utilisé",
            description = "Le solde n'a pas de date limite ; les achats s'additionnent. Un sondage est utilisé à la "
                    + "publication d'une lignée hors abonnement actif (une seule fois par lignée). Zéros si "
                    + "l'entreprise n'a jamais acheté.")
    public CreditsResponse credits(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi) {
        return abonnementMapper.toResponse(abonnementService.credits(moi.entrepriseId()));
    }

    @PostMapping("/achat-sondages/intention")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Acheter des sondages par carte (intention de paiement Stripe)",
            description = "Paiement unique de quantité × prix unitaire (PRIX_SONDAGE_CAD), même parcours qu'un "
                    + "abonnement : clientSecret pour Stripe.js, puis POST /paiement/confirmer, qui relit le paiement "
                    + "chez Stripe et crédite le solde une seule fois. 400 QUANTITE_INVALIDE hors de 1 à 1000.")
    public IntentionPaiementResponse acheterParCarte(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @Valid @RequestBody AchatSondagesRequest request) {
        return abonnementMapper.toResponse(
                abonnementService.creerIntentionAchat(moi.entrepriseId(), abonnementMapper.toModel(request)));
    }

    @PostMapping("/achat-sondages/paiement-mobile")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Acheter des sondages par paiement mobile (Moosyl, en MRU)",
            description = "Montant = quantité × PRIX_SONDAGE_MRU. Même parcours qu'un abonnement : redirection vers "
                    + "checkoutUrl, puis webhook Moosyl ou GET /paiement-mobile/{reference} ; le solde est crédité une "
                    + "seule fois. 400 QUANTITE_INVALIDE, 503 PAIEMENT_MOBILE_NON_CONFIGURE, 502 MOOSYL_ERREUR.")
    public PaiementMobileResponse acheterParMobile(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @Valid @RequestBody AchatSondagesMobileRequest request) {
        return abonnementMapper.toResponse(
                paiementMobileService.demarrerAchat(moi.entrepriseId(), abonnementMapper.toModel(request)));
    }

    @GetMapping("/paiements")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historique des paiements de mon entreprise",
            description = "Paiements réussis seulement (les intentions abandonnées ou échouées n'y sont pas), du plus "
                    + "récent au plus ancien : date, type (ABONNEMENT avec formule, ACHAT_SONDAGES avec quantite), "
                    + "montant, devise, fournisseur. Permis abonnement expiré ou en attente.")
    public PageResponse<PaiementResponse> paiements(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return abonnementMapper.toPaiementResponses(abonnementService.paiements(moi.entrepriseId(), page, taille));
    }

    @PostMapping("/paiement/intention")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une intention de paiement Stripe pour une formule",
            description = "Renvoie le clientSecret et la clé publique pour Stripe.js. L'abonnement n'est pas modifié "
                    + "tant que /paiement/confirmer n'a pas revérifié le paiement. Aucune donnée de carte ne passe "
                    + "par Batulo. Sans frontend : payez avec POST /api/dev/paiements/carte-test (profil dev).")
    public IntentionPaiementResponse creerIntention(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @Valid @RequestBody IntentionPaiementRequest request) {
        return abonnementMapper.toResponse(
                abonnementService.creerIntentionPaiement(moi.entrepriseId(), abonnementMapper.toModel(request)));
    }

    @PostMapping("/paiement/confirmer")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Confirmer un paiement (revérifié auprès de Stripe)",
            description = "Vérifie chez Stripe que le paiement est « succeeded » et appartient à l'entreprise, puis "
                    + "passe l'abonnement à ACTIF. Un renouvellement avant l'échéance prolonge depuis la date "
                    + "d'expiration. Idempotent : un même paiement n'est compté qu'une fois. "
                    + "Achat de sondages (POST /achat-sondages/intention) : même route, le solde est crédité de la "
                    + "quantité une seule fois. 400 PAIEMENT_NON_REUSSI si Stripe n'a pas encore validé le paiement.")
    public PaiementConfirmeResponse confirmer(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @Valid @RequestBody ConfirmationPaiementRequest request) {
        return abonnementMapper.toResponse(
                abonnementService.confirmerPaiement(moi.entrepriseId(), abonnementMapper.toModel(request)));
    }

    @PostMapping("/paiement-mobile")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Payer une formule par paiement mobile (Moosyl, en MRU)",
            description = "Crée le paiement EN_ATTENTE et une session Moosyl : redirigez l'admin vers checkoutUrl. "
                    + "Moosyl le renvoie ensuite sur l'URL de retour du frontend avec ?reference=. L'abonnement n'est "
                    + "activé qu'à la confirmation de Moosyl (webhook, ou relecture par GET .../{reference}). "
                    + "503 PAIEMENT_MOBILE_NON_CONFIGURE, 502 MOOSYL_ERREUR.")
    public PaiementMobileResponse payerMobile(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @Valid @RequestBody PaiementMobileRequest request) {
        return abonnementMapper.toResponse(
                paiementMobileService.demarrer(moi.entrepriseId(), abonnementMapper.toModel(request)));
    }

    @GetMapping("/paiement-mobile/{reference}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Statut d'un paiement mobile",
            description = "EN_ATTENTE, REUSSI ou ECHOUE, avec l'abonnement à jour. Tant que le paiement est en "
                    + "attente, la session est relue chez Moosyl (le webhook reste la source principale) ; un même "
                    + "paiement n'active ou ne prolonge l'abonnement qu'une fois. 404 PAIEMENT_INTROUVABLE.")
    public StatutPaiementMobileResponse statutMobile(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable String reference) {
        return abonnementMapper.toResponse(paiementMobileService.statut(moi.entrepriseId(), reference));
    }
}
