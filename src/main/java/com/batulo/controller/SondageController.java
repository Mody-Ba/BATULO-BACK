package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.SondageMapper;
import com.batulo.service.SondageService;
import com.batulo.service.model.OrdreRequest;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.QuestionRequest;
import com.batulo.service.model.SectionRequest;
import com.batulo.service.model.SondageRequest;
import com.batulo.service.model.SondageResponse;
import com.batulo.service.model.SondageResumeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Construction des formulaires dynamiques (module 4.1). ADMIN : tous les sondages de son entreprise.
 * GESTIONNAIRE : ses propres sondages, et seulement avec la permission de créer des sondages pour créer ou
 * modifier. Les règles d'abonnement s'appliquent (EXPIRE : lecture seule). Toute modification d'une section ou
 * d'une question renvoie le sondage complet.
 */
@RestController
@RequestMapping("/api/sondages")
@Tag(name = "Sondages", description = "Formulaires dynamiques : sondage > sections > questions > options (module 4.1)")
public class SondageController {

    private static final String ROLES = "hasAnyRole('ADMIN', 'GESTIONNAIRE')";

    private final SondageService sondageService;
    private final SondageMapper sondageMapper;

    public SondageController(SondageService sondageService, SondageMapper sondageMapper) {
        this.sondageService = sondageService;
        this.sondageMapper = sondageMapper;
    }

    @PostMapping
    @PreAuthorize(ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un sondage", description = "Sans section. 403 PERMISSION_SONDAGES_REQUISE pour un "
            + "gestionnaire sans la permission de créer des sondages.")
    public SondageResponse creer(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                 @Valid @RequestBody SondageRequest request) {
        return sondageMapper.toResponse(sondageService.creer(moi.id(), sondageMapper.toModel(request)));
    }

    @GetMapping
    @PreAuthorize(ROLES)
    @Operation(summary = "Lister les sondages",
            description = "Admin : tous ceux de l'entreprise ; gestionnaire : ceux qu'il a créés. Du plus récemment "
                    + "modifié au plus ancien, avec le nombre de sections et de questions.")
    public PageResponse<SondageResumeResponse> lister(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return sondageMapper.toResponse(sondageService.lister(moi.id(), page, taille));
    }

    @GetMapping("/{id}")
    @PreAuthorize(ROLES)
    @Operation(summary = "Sondage complet", description = "Sections > questions > options, triées par ordre.")
    public SondageResponse detail(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                  @PathVariable Long id) {
        return sondageMapper.toResponse(sondageService.detail(moi.id(), id));
    }

    @PutMapping("/{id}")
    @PreAuthorize(ROLES)
    @Operation(summary = "Modifier le titre et la description")
    public SondageResponse modifier(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                    @PathVariable Long id, @Valid @RequestBody SondageRequest request) {
        return sondageMapper.toResponse(sondageService.modifier(moi.id(), id, sondageMapper.toModel(request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un sondage", description = "Avec ses sections, questions et options. Irréversible.")
    public void supprimer(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                          @PathVariable Long id) {
        sondageService.supprimer(moi.id(), id);
    }

    @PostMapping("/{id}/sections")
    @PreAuthorize(ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une section", description = "Ajoutée à la fin. Renvoie le sondage complet.")
    public SondageResponse ajouterSection(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                          @PathVariable Long id, @Valid @RequestBody SectionRequest request) {
        return sondageMapper.toResponse(sondageService.ajouterSection(moi.id(), id, sondageMapper.toModel(request)));
    }

    @PutMapping("/{id}/sections/ordre")
    @PreAuthorize(ROLES)
    @Operation(summary = "Réordonner les sections (glisser-déposer)",
            description = "Tous les ids des sections, chacun une fois, dans le nouvel ordre. 400 ORDRE_INVALIDE sinon.")
    public SondageResponse ordonnerSections(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                            @PathVariable Long id, @Valid @RequestBody OrdreRequest request) {
        return sondageMapper.toResponse(sondageService.ordonnerSections(moi.id(), id, request.ids()));
    }

    @PutMapping("/{id}/sections/{sectionId}")
    @PreAuthorize(ROLES)
    @Operation(summary = "Modifier une section")
    public SondageResponse modifierSection(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                           @PathVariable Long id, @PathVariable Long sectionId,
                                           @Valid @RequestBody SectionRequest request) {
        return sondageMapper.toResponse(
                sondageService.modifierSection(moi.id(), id, sectionId, sondageMapper.toModel(request)));
    }

    @DeleteMapping("/{id}/sections/{sectionId}")
    @PreAuthorize(ROLES)
    @Operation(summary = "Supprimer une section", description = "Avec ses questions ; les sections sont renumérotées.")
    public SondageResponse supprimerSection(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                            @PathVariable Long id, @PathVariable Long sectionId) {
        return sondageMapper.toResponse(sondageService.supprimerSection(moi.id(), id, sectionId));
    }

    @PostMapping("/{id}/sections/{sectionId}/questions")
    @PreAuthorize(ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une question",
            description = "Ajoutée à la fin de la section. Configuration et options validées selon le type "
                    + "(400 QUESTION_INVALIDE, VALEUR_OPTION_EN_DOUBLE).")
    public SondageResponse ajouterQuestion(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                           @PathVariable Long id, @PathVariable Long sectionId,
                                           @Valid @RequestBody QuestionRequest request) {
        return sondageMapper.toResponse(
                sondageService.ajouterQuestion(moi.id(), id, sectionId, sondageMapper.toModel(request)));
    }

    @PutMapping("/{id}/sections/{sectionId}/questions/ordre")
    @PreAuthorize(ROLES)
    @Operation(summary = "Réordonner les questions d'une section (glisser-déposer)",
            description = "Tous les ids des questions de la section, chacun une fois. 400 ORDRE_INVALIDE sinon.")
    public SondageResponse ordonnerQuestions(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                             @PathVariable Long id, @PathVariable Long sectionId,
                                             @Valid @RequestBody OrdreRequest request) {
        return sondageMapper.toResponse(sondageService.ordonnerQuestions(moi.id(), id, sectionId, request.ids()));
    }

    @PutMapping("/{id}/questions/{questionId}")
    @PreAuthorize(ROLES)
    @Operation(summary = "Modifier une question",
            description = "Le type peut changer. Les options envoyées remplacent les anciennes : gardez l'« id » "
                    + "d'une option existante pour conserver sa valeur, même si son libellé change.")
    public SondageResponse modifierQuestion(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                            @PathVariable Long id, @PathVariable Long questionId,
                                            @Valid @RequestBody QuestionRequest request) {
        return sondageMapper.toResponse(
                sondageService.modifierQuestion(moi.id(), id, questionId, sondageMapper.toModel(request)));
    }

    @DeleteMapping("/{id}/questions/{questionId}")
    @PreAuthorize(ROLES)
    @Operation(summary = "Supprimer une question", description = "Les questions de sa section sont renumérotées.")
    public SondageResponse supprimerQuestion(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                             @PathVariable Long id, @PathVariable Long questionId) {
        return sondageMapper.toResponse(sondageService.supprimerQuestion(moi.id(), id, questionId));
    }
}
