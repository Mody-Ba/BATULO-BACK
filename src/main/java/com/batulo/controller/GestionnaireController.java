package com.batulo.controller;

import com.batulo.mappers.GestionnaireMapper;
import com.batulo.service.GestionnaireService;
import com.batulo.service.model.CreationGestionnaireRequest;
import com.batulo.service.model.ModificationGestionnaireRequest;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.PermissionSondagesRequest;
import com.batulo.service.model.UtilisateurResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
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
 * Espace admin : gestion des gestionnaires de son entreprise (AD-07 à AD-10).
 * Selon l'abonnement : EN_ATTENTE_PAIEMENT ou DESACTIVE, tout est refusé (403) ; EXPIRE, lecture seule.
 */
@RestController
@RequestMapping("/api/admin/gestionnaires")
@Tag(name = "Admin – gestionnaires", description = "Gestionnaires de l'entreprise de l'admin (AD-07 à AD-10)")
public class GestionnaireController {

    // TEMPORAIRE - étape 7 : l'admin connecté viendra du jeton JWT, plus d'un paramètre.
    private static final String DESCRIPTION_ADMIN = "TEMPORAIRE - étape 7 : id de l'admin connecté (sera lu dans le jeton JWT)";

    private final GestionnaireService gestionnaireService;
    private final GestionnaireMapper gestionnaireMapper;

    public GestionnaireController(GestionnaireService gestionnaireService, GestionnaireMapper gestionnaireMapper) {
        this.gestionnaireService = gestionnaireService;
        this.gestionnaireMapper = gestionnaireMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un gestionnaire (AD-07)",
            description = "Mot de passe temporaire valide 24 h, jamais renvoyé ici (logs en profil dev avant l'étape 6). "
                    + "Sans permission de créer des sondages. 409 si le courriel est déjà utilisé sur la plateforme.")
    public UtilisateurResponse creer(
            // TEMPORAIRE - étape 7
            @Parameter(description = DESCRIPTION_ADMIN) @RequestParam Long adminId,
            @Valid @RequestBody CreationGestionnaireRequest request) {
        return gestionnaireMapper.toResponse(gestionnaireService.creer(adminId, gestionnaireMapper.toModel(request)));
    }

    @GetMapping
    @Operation(summary = "Lister mes gestionnaires (AD-10)", description = "Paginé, du plus récent au plus ancien.")
    public PageResponse<UtilisateurResponse> lister(
            // TEMPORAIRE - étape 7
            @Parameter(description = DESCRIPTION_ADMIN) @RequestParam Long adminId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return gestionnaireMapper.toResponse(gestionnaireService.lister(adminId, page, taille));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un gestionnaire", description = "404 s'il n'appartient pas à l'entreprise de l'admin.")
    public UtilisateurResponse detail(
            // TEMPORAIRE - étape 7
            @Parameter(description = DESCRIPTION_ADMIN) @RequestParam Long adminId,
            @PathVariable Long id) {
        return gestionnaireMapper.toResponse(gestionnaireService.detail(adminId, id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un gestionnaire (AD-08)",
            description = "Prénom, nom et courriel. 409 si le courriel est déjà utilisé par un autre compte.")
    public UtilisateurResponse modifier(
            // TEMPORAIRE - étape 7
            @Parameter(description = DESCRIPTION_ADMIN) @RequestParam Long adminId,
            @PathVariable Long id,
            @Valid @RequestBody ModificationGestionnaireRequest request) {
        return gestionnaireMapper.toResponse(
                gestionnaireService.modifier(adminId, id, gestionnaireMapper.toModel(request)));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un gestionnaire (AD-08)", description = "Irréversible.")
    public void supprimer(
            // TEMPORAIRE - étape 7
            @Parameter(description = DESCRIPTION_ADMIN) @RequestParam Long adminId,
            @PathVariable Long id) {
        gestionnaireService.supprimer(adminId, id);
    }

    @PatchMapping("/{id}/desactiver")
    @Operation(summary = "Désactiver un gestionnaire (AD-08)", description = "Il ne pourra plus se connecter.")
    public UtilisateurResponse desactiver(
            // TEMPORAIRE - étape 7
            @Parameter(description = DESCRIPTION_ADMIN) @RequestParam Long adminId,
            @PathVariable Long id) {
        return gestionnaireMapper.toResponse(gestionnaireService.changerActivation(adminId, id, false));
    }

    @PatchMapping("/{id}/activer")
    @Operation(summary = "Activer un gestionnaire (AD-08)")
    public UtilisateurResponse activer(
            // TEMPORAIRE - étape 7
            @Parameter(description = DESCRIPTION_ADMIN) @RequestParam Long adminId,
            @PathVariable Long id) {
        return gestionnaireMapper.toResponse(gestionnaireService.changerActivation(adminId, id, true));
    }

    @PatchMapping("/{id}/permission-sondages")
    @Operation(summary = "Autoriser ou retirer la création de sondages (AD-09)")
    public UtilisateurResponse changerPermissionSondages(
            // TEMPORAIRE - étape 7
            @Parameter(description = DESCRIPTION_ADMIN) @RequestParam Long adminId,
            @PathVariable Long id,
            @Valid @RequestBody PermissionSondagesRequest request) {
        return gestionnaireMapper.toResponse(
                gestionnaireService.changerPermissionSondages(adminId, id, request.peutCreerSondages()));
    }

    @PostMapping("/{id}/mot-de-passe-temporaire")
    @Operation(summary = "Renvoyer un mot de passe temporaire",
            description = "Nouveau mot de passe valide 24 h ; l'ancien ne fonctionne plus. Jamais renvoyé ici "
                    + "(logs en profil dev avant l'étape 6).")
    public UtilisateurResponse renvoyerMotDePasse(
            // TEMPORAIRE - étape 7
            @Parameter(description = DESCRIPTION_ADMIN) @RequestParam Long adminId,
            @PathVariable Long id) {
        return gestionnaireMapper.toResponse(gestionnaireService.renvoyerMotDePasseTemporaire(adminId, id));
    }
}
