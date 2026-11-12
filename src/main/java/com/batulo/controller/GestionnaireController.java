package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.AssignationMapper;
import com.batulo.mappers.GestionnaireMapper;
import com.batulo.service.AssignationService;
import com.batulo.service.GestionnaireService;
import com.batulo.service.model.CreationGestionnaireRequest;
import com.batulo.service.model.ModificationGestionnaireRequest;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.PermissionSondagesRequest;
import com.batulo.service.model.SondageGestionnaireResponse;
import com.batulo.service.model.UtilisateurResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Espace admin : gestion des gestionnaires de son entreprise (AD-07 à AD-10), réservé au rôle ADMIN.
 * L'entreprise est celle de l'admin du jeton. Les règles d'abonnement (EN_ATTENTE_PAIEMENT ou DESACTIVE :
 * tout est refusé ; EXPIRE : lecture seule) sont appliquées par AccesAbonnementInterceptor.
 */
@RestController
@RequestMapping("/api/admin/gestionnaires")
@Tag(name = "Admin – gestionnaires", description = "Gestionnaires de l'entreprise de l'admin (AD-07 à AD-10)")
public class GestionnaireController {

    private final GestionnaireService gestionnaireService;
    private final GestionnaireMapper gestionnaireMapper;
    private final AssignationService assignationService;
    private final AssignationMapper assignationMapper;

    public GestionnaireController(GestionnaireService gestionnaireService, GestionnaireMapper gestionnaireMapper,
                                  AssignationService assignationService, AssignationMapper assignationMapper) {
        this.gestionnaireService = gestionnaireService;
        this.gestionnaireMapper = gestionnaireMapper;
        this.assignationService = assignationService;
        this.assignationMapper = assignationMapper;
    }

    @GetMapping("/{id}/sondages")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sondages d'un gestionnaire (AD-10, GE-04)",
            description = "Ceux qu'il voit : créés par lui (relation CREATEUR) ou assignés (ASSIGNE). Paginé, du plus "
                    + "récemment modifié au plus ancien.")
    public PageResponse<SondageGestionnaireResponse> sondages(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return assignationMapper.toResponse(assignationService.sondagesDuGestionnaire(moi.id(), id, page, taille));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un gestionnaire (AD-07)",
            description = "Mot de passe temporaire valide 24 h, jamais renvoyé ici (logs en profil dev avant l'étape 6). "
                    + "Sans permission de créer des sondages. 409 si le courriel est déjà utilisé sur la plateforme.")
    public UtilisateurResponse creer(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @Valid @RequestBody CreationGestionnaireRequest request) {
        return gestionnaireMapper.toResponse(gestionnaireService.creer(moi.id(), gestionnaireMapper.toModel(request)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lister mes gestionnaires (AD-10)",
            description = "Paginé, du plus récent au plus ancien. Filtres facultatifs et cumulables : recherche dans "
                    + "le prénom, le nom ou le courriel (sans tenir compte des majuscules ni des accents) et actif.")
    public PageResponse<UtilisateurResponse> lister(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @Parameter(description = "Facultatif : texte cherché dans le prénom, le nom ou le courriel")
            @RequestParam(required = false) @Size(max = 100, message = "recherche ne doit pas dépasser 100 caractères")
            String recherche,
            @Parameter(description = "Facultatif : true (actifs) ou false (désactivés)")
            @RequestParam(required = false) Boolean actif,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return gestionnaireMapper.toResponse(gestionnaireService.lister(moi.id(), recherche, actif, page, taille));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Détail d'un gestionnaire", description = "404 s'il n'appartient pas à l'entreprise de l'admin.")
    public UtilisateurResponse detail(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long id) {
        return gestionnaireMapper.toResponse(gestionnaireService.detail(moi.id(), id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Modifier un gestionnaire (AD-08)",
            description = "Prénom, nom et courriel. 409 si le courriel est déjà utilisé par un autre compte.")
    public UtilisateurResponse modifier(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long id,
            @Valid @RequestBody ModificationGestionnaireRequest request) {
        return gestionnaireMapper.toResponse(
                gestionnaireService.modifier(moi.id(), id, gestionnaireMapper.toModel(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un gestionnaire (AD-08)", description = "Irréversible.")
    public void supprimer(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long id) {
        gestionnaireService.supprimer(moi.id(), id);
    }

    @PatchMapping("/{id}/desactiver")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver un gestionnaire (AD-08)", description = "Il ne pourra plus se connecter.")
    public UtilisateurResponse desactiver(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long id) {
        return gestionnaireMapper.toResponse(gestionnaireService.changerActivation(moi.id(), id, false));
    }

    @PatchMapping("/{id}/activer")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activer un gestionnaire (AD-08)")
    public UtilisateurResponse activer(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long id) {
        return gestionnaireMapper.toResponse(gestionnaireService.changerActivation(moi.id(), id, true));
    }

    @PatchMapping("/{id}/permission-sondages")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Autoriser ou retirer la création de sondages (AD-09)")
    public UtilisateurResponse changerPermissionSondages(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long id,
            @Valid @RequestBody PermissionSondagesRequest request) {
        return gestionnaireMapper.toResponse(
                gestionnaireService.changerPermissionSondages(moi.id(), id, request.peutCreerSondages()));
    }

    @PostMapping("/{id}/mot-de-passe-temporaire")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Renvoyer un mot de passe temporaire",
            description = "Nouveau mot de passe valide 24 h ; l'ancien ne fonctionne plus. Jamais renvoyé ici "
                    + "(logs en profil dev avant l'étape 6).")
    public UtilisateurResponse renvoyerMotDePasse(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long id) {
        return gestionnaireMapper.toResponse(gestionnaireService.renvoyerMotDePasseTemporaire(moi.id(), id));
    }
}
