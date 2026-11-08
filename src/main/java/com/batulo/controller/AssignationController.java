package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.AssignationMapper;
import com.batulo.service.AssignationService;
import com.batulo.service.model.AssignationRequest;
import com.batulo.service.model.AssignationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Assignation des sondages aux gestionnaires (AD-10, RG-03), réservée à l'ADMIN. L'id d'une version quelconque
 * désigne le sondage : toutes ses versions partagent les mêmes assignations.
 */
@RestController
@RequestMapping("/api/sondages/{id}/assignations")
@Tag(name = "Sondages – assignations", description = "Assigner un sondage à des gestionnaires (AD-10)")
public class AssignationController {

    private final AssignationService assignationService;
    private final AssignationMapper assignationMapper;

    public AssignationController(AssignationService assignationService, AssignationMapper assignationMapper) {
        this.assignationService = assignationService;
        this.assignationMapper = assignationMapper;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assigner le sondage à des gestionnaires",
            description = "Toutes les versions du sondage. Un gestionnaire déjà assigné est ignoré (pas de doublon ni "
                    + "de nouvelle notification). Notification et courriel aux nouveaux. 404 GESTIONNAIRE_INTROUVABLE, "
                    + "409 GESTIONNAIRE_DESACTIVE ou GESTIONNAIRE_CREATEUR. Renvoie la liste à jour.")
    public List<AssignationResponse> assigner(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                              @PathVariable Long id, @Valid @RequestBody AssignationRequest request) {
        return assignationMapper.toResponses(assignationService.assigner(moi.id(), id, request.gestionnaireIds()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Gestionnaires assignés au sondage")
    public List<AssignationResponse> gestionnaires(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                                   @PathVariable Long id) {
        return assignationMapper.toResponses(assignationService.gestionnairesDuSondage(moi.id(), id));
    }

    @DeleteMapping("/{gestionnaireId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retirer une assignation",
            description = "Le gestionnaire ne voit plus le sondage (sauf s'il en a créé une version) ; notification "
                    + "dans son tableau de bord. 404 ASSIGNATION_INTROUVABLE.")
    public void retirer(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                        @PathVariable Long id, @PathVariable Long gestionnaireId) {
        assignationService.retirer(moi.id(), id, gestionnaireId);
    }
}
