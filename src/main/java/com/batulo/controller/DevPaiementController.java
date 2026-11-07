package com.batulo.controller;

import com.batulo.mappers.AbonnementMapper;
import com.batulo.service.DevPaiementService;
import com.batulo.service.model.CarteTestResponse;
import com.batulo.service.model.ConfirmationPaiementRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Route de DÉVELOPPEMENT, créée uniquement avec le profil dev : remplace le frontend (Stripe.js) pour payer
 * un PaymentIntent avec la carte de test Stripe. N'existe pas en production.
 */
@RestController
@Profile("dev")
@RequestMapping("/api/dev/paiements")
@Tag(name = "Développement (profil dev)", description = "Outils de test, absents en production")
public class DevPaiementController {

    private final DevPaiementService devPaiementService;
    private final AbonnementMapper abonnementMapper;

    public DevPaiementController(DevPaiementService devPaiementService, AbonnementMapper abonnementMapper) {
        this.devPaiementService = devPaiementService;
        this.abonnementMapper = abonnementMapper;
    }

    @PostMapping("/carte-test")
    @Operation(summary = "Payer un PaymentIntent avec la carte de test pm_card_visa (dev)",
            description = "Simule la saisie de carte du frontend : confirme le PaymentIntent côté Stripe. "
                    + "N'active PAS l'abonnement : appelez ensuite POST /api/abonnements/paiement/confirmer.")
    public CarteTestResponse payerAvecCarteTest(
            // TEMPORAIRE - étape 7
            @Parameter(description = "TEMPORAIRE - étape 7 : entreprise de l'admin connecté")
            @RequestParam Long entrepriseId,
            @Valid @RequestBody ConfirmationPaiementRequest request) {
        return abonnementMapper.toCarteTestResponse(
                devPaiementService.payerAvecCarteTest(entrepriseId, abonnementMapper.toModel(request)));
    }
}
