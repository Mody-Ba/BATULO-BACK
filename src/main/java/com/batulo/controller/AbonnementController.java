package com.batulo.controller;

import com.batulo.mappers.AbonnementMapper;
import com.batulo.service.AbonnementService;
import com.batulo.service.model.AbonnementResponse;
import com.batulo.service.model.ConfirmationPaiementRequest;
import com.batulo.service.model.FormuleResponse;
import com.batulo.service.model.IntentionPaiementRequest;
import com.batulo.service.model.IntentionPaiementResponse;
import com.batulo.service.model.PaiementConfirmeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
 */
@RestController
@RequestMapping("/api/abonnements")
@Tag(name = "Abonnement", description = "Formules, abonnement de l'admin et paiement Stripe (mode test)")
public class AbonnementController {

    // TEMPORAIRE - étape 7 : l'entreprise viendra de l'admin connecté (jeton JWT), plus d'un paramètre.
    private static final String DESCRIPTION_ENTREPRISE =
            "TEMPORAIRE - étape 7 : entreprise de l'admin connecté (sera lue dans le jeton JWT)";

    private final AbonnementService abonnementService;
    private final AbonnementMapper abonnementMapper;

    public AbonnementController(AbonnementService abonnementService, AbonnementMapper abonnementMapper) {
        this.abonnementService = abonnementService;
        this.abonnementMapper = abonnementMapper;
    }

    @GetMapping("/formules")
    @Operation(summary = "Formules disponibles et prix (public)")
    public List<FormuleResponse> formules() {
        return abonnementService.formules().stream().map(abonnementMapper::toResponse).toList();
    }

    @GetMapping("/moi")
    @Operation(summary = "Mon abonnement : formule, statut, date d'expiration, jours restants",
            description = "Le statut est recalculé à partir des dates. 404 si l'entreprise n'a pas d'abonnement.")
    public AbonnementResponse monAbonnement(
            // TEMPORAIRE - étape 7
            @Parameter(description = DESCRIPTION_ENTREPRISE) @RequestParam Long entrepriseId) {
        return abonnementMapper.toResponse(abonnementService.monAbonnement(entrepriseId));
    }

    @PostMapping("/paiement/intention")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une intention de paiement Stripe pour une formule",
            description = "Renvoie le clientSecret et la clé publique pour Stripe.js. L'abonnement n'est pas modifié "
                    + "tant que /paiement/confirmer n'a pas revérifié le paiement. Aucune donnée de carte ne passe "
                    + "par Batulo. Sans frontend : payez avec POST /api/dev/paiements/carte-test (profil dev).")
    public IntentionPaiementResponse creerIntention(
            // TEMPORAIRE - étape 7
            @Parameter(description = DESCRIPTION_ENTREPRISE) @RequestParam Long entrepriseId,
            @Valid @RequestBody IntentionPaiementRequest request) {
        return abonnementMapper.toResponse(
                abonnementService.creerIntentionPaiement(entrepriseId, abonnementMapper.toModel(request)));
    }

    @PostMapping("/paiement/confirmer")
    @Operation(summary = "Confirmer un paiement (revérifié auprès de Stripe)",
            description = "Vérifie chez Stripe que le paiement est « succeeded » et appartient à l'entreprise, puis "
                    + "passe l'abonnement à ACTIF. Un renouvellement avant l'échéance prolonge depuis la date "
                    + "d'expiration. Idempotent : un même paiement n'est compté qu'une fois. "
                    + "400 PAIEMENT_NON_REUSSI si Stripe n'a pas encore validé le paiement.")
    public PaiementConfirmeResponse confirmer(
            // TEMPORAIRE - étape 7
            @Parameter(description = DESCRIPTION_ENTREPRISE) @RequestParam Long entrepriseId,
            @Valid @RequestBody ConfirmationPaiementRequest request) {
        return abonnementMapper.toResponse(
                abonnementService.confirmerPaiement(entrepriseId, abonnementMapper.toModel(request)));
    }
}
