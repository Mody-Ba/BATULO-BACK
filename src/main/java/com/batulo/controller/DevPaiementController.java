package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.AbonnementMapper;
import com.batulo.service.DevPaiementService;
import com.batulo.service.model.CarteTestResponse;
import com.batulo.service.model.ConfirmationPaiementRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Payer un PaymentIntent avec la carte de test pm_card_visa (dev)",
            description = "Simule la saisie de carte du frontend : confirme le PaymentIntent côté Stripe. "
                    + "N'active PAS l'abonnement : appelez ensuite POST /api/abonnements/paiement/confirmer.")
    public CarteTestResponse payerAvecCarteTest(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @Valid @RequestBody ConfirmationPaiementRequest request) {
        return abonnementMapper.toCarteTestResponse(
                devPaiementService.payerAvecCarteTest(moi.entrepriseId(), abonnementMapper.toModel(request)));
    }
}
