package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.enums.StatutDemande;
import com.batulo.mappers.MessagerieMapper;
import com.batulo.service.DemandeAbonnementService;
import com.batulo.service.model.DemandeAbonnementRequest;
import com.batulo.service.model.DemandeAbonnementResponse;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.TraitementDemandeRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demandes d'augmentation d'abonnement (AD-05, SA-07). Purement informatives (RG-09) : acceptée, l'admin paie
 * lui-même la formule par le parcours Stripe habituel.
 */
@RestController
@Validated
@Tag(name = "Demandes d'abonnement", description = "L'admin demande une augmentation ; le super admin répond")
public class DemandeAbonnementController {

    private final DemandeAbonnementService demandeService;
    private final MessagerieMapper messagerieMapper;

    public DemandeAbonnementController(DemandeAbonnementService demandeService, MessagerieMapper messagerieMapper) {
        this.demandeService = demandeService;
        this.messagerieMapper = messagerieMapper;
    }

    @PostMapping("/api/abonnements/demandes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Demander une augmentation d'abonnement",
            description = "Postée dans la conversation avec Batulo ; les super admins sont notifiés. Une seule "
                    + "demande en attente à la fois (409 DEMANDE_EN_COURS). Possible même abonnement expiré.")
    public DemandeAbonnementResponse demander(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                              @Valid @RequestBody DemandeAbonnementRequest request) {
        return messagerieMapper.toResponse(demandeService.demander(moi.id(), request.formuleSouhaitee(),
                request.message()));
    }

    @GetMapping("/api/abonnements/demandes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Mes demandes d'abonnement", description = "Les plus récentes d'abord.")
    public PageResponse<DemandeAbonnementResponse> mesDemandes(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return messagerieMapper.toResponse(demandeService.mesDemandes(moi.id(), page, taille),
                messagerieMapper::toResponse);
    }

    @GetMapping("/api/super-admin/demandes-abonnement")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Demandes d'abonnement reçues (SA-07)",
            description = "Toutes, ou filtrées par statut ; avec l'entreprise, sa formule et le statut de son abonnement.")
    public PageResponse<DemandeAbonnementResponse> lister(
            @RequestParam(required = false) StatutDemande statut,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return messagerieMapper.toResponse(demandeService.lister(statut, page, taille), messagerieMapper::toResponse);
    }

    @PatchMapping("/api/super-admin/demandes-abonnement/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Répondre à une demande (SA-07)",
            description = "TRAITEE (acceptée) ou REFUSEE, depuis EN_ATTENTE seulement (409 DEMANDE_DEJA_TRAITEE). "
                    + "La réponse est obligatoire pour un refus (400 REPONSE_OBLIGATOIRE), facultative sinon. "
                    + "La réponse est postée dans la conversation et l'admin est notifié. Aucun effet sur l'abonnement.")
    public DemandeAbonnementResponse traiter(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                             @PathVariable Long id,
                                             @Valid @RequestBody TraitementDemandeRequest request) {
        return messagerieMapper.toResponse(demandeService.traiter(moi.id(), id, request.statut(), request.reponse()));
    }
}
