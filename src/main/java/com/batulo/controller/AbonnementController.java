package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.AbonnementMapper;
import com.batulo.service.AbonnementService;
import com.batulo.service.model.AbonnementResponse;
import com.batulo.service.model.ConfirmationPaiementRequest;
import com.batulo.service.model.FormuleResponse;
import com.batulo.service.model.IntentionPaiementRequest;
import com.batulo.service.model.IntentionPaiementResponse;
import com.batulo.service.model.PageResponse;
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
@Tag(name = "Abonnement", description = "Formules, abonnement de l'admin et paiement Stripe (mode test)")
public class AbonnementController {

    private final AbonnementService abonnementService;
    private final AbonnementMapper abonnementMapper;

    public AbonnementController(AbonnementService abonnementService, AbonnementMapper abonnementMapper) {
        this.abonnementService = abonnementService;
        this.abonnementMapper = abonnementMapper;
    }

    @GetMapping("/formules")
    @SecurityRequirements
    @Operation(summary = "Formules disponibles et prix (public)")
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

    @GetMapping("/paiements")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Historique des paiements de mon entreprise",
            description = "Paiements réussis seulement (les intentions abandonnées ou échouées n'y sont pas), du plus "
                    + "récent au plus ancien : date, formule, montant, devise. Permis abonnement expiré ou en attente.")
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
                    + "400 PAIEMENT_NON_REUSSI si Stripe n'a pas encore validé le paiement.")
    public PaiementConfirmeResponse confirmer(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @Valid @RequestBody ConfirmationPaiementRequest request) {
        return abonnementMapper.toResponse(
                abonnementService.confirmerPaiement(moi.entrepriseId(), abonnementMapper.toModel(request)));
    }
}
