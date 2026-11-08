package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.enums.StatutSondage;
import com.batulo.mappers.SondageMapper;
import com.batulo.service.SondageService;
import com.batulo.service.model.ApercuResponse;
import com.batulo.service.model.CreationSondageRequest;
import com.batulo.service.model.DatesSondageRequest;
import com.batulo.service.model.FormulaireResponse;
import com.batulo.service.model.ModeleRequest;
import com.batulo.service.model.OrdreRequest;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.QuestionRequest;
import com.batulo.service.model.SectionRequest;
import com.batulo.service.model.SondageRequest;
import com.batulo.service.model.SondageResponse;
import com.batulo.service.model.SondageResumeResponse;
import com.batulo.service.model.VersionResponse;
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

import java.util.List;

/**
 * Construction des formulaires dynamiques (module 4.1). ADMIN : tous les sondages de son entreprise.
 * GESTIONNAIRE : ses propres sondages, et seulement avec la permission de créer des sondages pour créer ou
 * modifier. Les règles d'abonnement s'appliquent (EXPIRE : lecture seule). Toute modification d'une section ou
 * d'une question renvoie le sondage complet.
 * Seul un brouillon est modifiable : un sondage publié est gelé (409 SONDAGE_PUBLIE) et se modifie par une
 * nouvelle version. Statut calculé à partir des dates : NON_COMMENCE, EN_COURS, TERMINE.
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
    @Operation(summary = "Créer un sondage (vide ou à partir d'un formulaire existant)",
            description = "Sans sondageSourceId : sondage vide. Avec : copie des sections, questions et options d'un "
                    + "sondage de l'entreprise (même TERMINE), avec les mêmes valeurs d'options ; ni dates, ni "
                    + "publication, ni réponses ; la source n'est pas modifiée. 403 PERMISSION_SONDAGES_REQUISE pour un "
                    + "gestionnaire sans la permission de créer des sondages, 404 si la source n'est pas visible.")
    public SondageResponse creer(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                 @Valid @RequestBody CreationSondageRequest request) {
        return sondageMapper.toResponse(sondageService.creer(moi.id(), sondageMapper.toModel(request)));
    }

    @GetMapping
    @PreAuthorize(ROLES)
    @Operation(summary = "Lister les sondages",
            description = "Admin : tous ceux de l'entreprise ; gestionnaire : ceux qu'il a créés. Du plus récemment "
                    + "modifié au plus ancien, avec le statut, la version et le nombre de sections et de questions.")
    public PageResponse<SondageResumeResponse> lister(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @Parameter(description = "Facultatif : NON_COMMENCE, EN_COURS ou TERMINE")
            @RequestParam(required = false) StatutSondage statut,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return sondageMapper.toResponse(sondageService.lister(moi.id(), statut, page, taille));
    }

    @GetMapping("/formulaires")
    @PreAuthorize(ROLES)
    @Operation(summary = "Formulaires réutilisables",
            description = "Sondages pouvant servir de base à un nouveau sondage (POST /api/sondages avec "
                    + "sondageSourceId) : titre, nombre de questions, date. Admin : tous ceux de l'entreprise ; "
                    + "gestionnaire (avec permission) : ceux qu'il voit. Modèles en premier.")
    public PageResponse<FormulaireResponse> formulaires(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @Parameter(description = "true : modèles seulement ; false (défaut) : tous")
            @RequestParam(defaultValue = "false") boolean modelesSeulement,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return sondageMapper.toFormulaireResponses(sondageService.formulaires(moi.id(), modelesSeulement, page, taille));
    }

    @PutMapping("/{id}/modele")
    @PreAuthorize(ROLES)
    @Operation(summary = "Marquer ou démarquer comme modèle",
            description = "Permis quel que soit le statut (ce n'est pas une modification du contenu).")
    public SondageResponse marquerModele(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                         @PathVariable Long id, @Valid @RequestBody ModeleRequest request) {
        return sondageMapper.toResponse(sondageService.marquerModele(moi.id(), id, request.estModele()));
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
    @Operation(summary = "Supprimer un brouillon",
            description = "Avec ses sections, questions et options. Irréversible. 409 SONDAGE_PUBLIE pour un sondage "
                    + "publié : il se ferme mais ne se supprime pas.")
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

    // ---------------------------------------------------------------- Publication et versions

    @PostMapping("/{id}/publier")
    @PreAuthorize(ROLES)
    @Operation(summary = "Publier un brouillon",
            description = "Corps facultatif. Ouverture maintenant ou planifiée (datePublication), fermeture facultative "
                    + "(dateFermeture). Le contenu est ensuite gelé. Ferme la version précédente au moment où celle-ci "
                    + "s'ouvre. 400 SONDAGE_INCOMPLET (aucune question, section vide) ou DATES_INVALIDES.")
    public SondageResponse publier(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                   @PathVariable Long id, @RequestBody(required = false) DatesSondageRequest request) {
        return sondageMapper.toResponse(sondageService.publier(moi.id(), id, sondageMapper.toModel(request)));
    }

    @PutMapping("/{id}/dates")
    @PreAuthorize(ROLES)
    @Operation(summary = "Ajuster les dates d'un sondage publié",
            description = "datePublication absente : inchangée (modifiable seulement avant l'ouverture). "
                    + "dateFermeture absente : pas de fermeture prévue. 409 si le sondage est un brouillon ou terminé.")
    public SondageResponse modifierDates(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                         @PathVariable Long id, @RequestBody DatesSondageRequest request) {
        return sondageMapper.toResponse(sondageService.modifierDates(moi.id(), id, sondageMapper.toModel(request)));
    }

    @PostMapping("/{id}/fermer")
    @PreAuthorize(ROLES)
    @Operation(summary = "Fermer un sondage", description = "Définitif : TERMINE immédiatement. Pour le rouvrir, "
            + "créez une nouvelle version. 409 si c'est un brouillon ou s'il est déjà terminé.")
    public SondageResponse fermer(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                  @PathVariable Long id) {
        return sondageMapper.toResponse(sondageService.fermer(moi.id(), id));
    }

    @GetMapping("/{id}/apercu")
    @PreAuthorize(ROLES)
    @Operation(summary = "Prévisualiser", description = "Le formulaire tel que le verra le répondant, sans les "
            + "informations internes. Fonctionne aussi pour un brouillon.")
    public ApercuResponse apercu(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                 @PathVariable Long id) {
        return sondageMapper.toResponse(sondageService.apercu(moi.id(), id));
    }

    @PostMapping("/{id}/dupliquer")
    @PreAuthorize(ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dupliquer", description = "Nouveau brouillon indépendant « Copie de ... » (version 1), "
            + "avec tout le contenu. Possible quel que soit le statut.")
    public SondageResponse dupliquer(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                     @PathVariable Long id) {
        return sondageMapper.toResponse(sondageService.dupliquer(moi.id(), id));
    }

    @PostMapping("/{id}/nouvelle-version")
    @PreAuthorize(ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une nouvelle version",
            description = "Seule façon de modifier un sondage publié : brouillon version N+1 avec tout le contenu "
                    + "(valeurs des options conservées). À partir de la dernière version publiée seulement. "
                    + "409 SONDAGE_BROUILLON, BROUILLON_EXISTANT ou VERSION_OBSOLETE.")
    public SondageResponse nouvelleVersion(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                           @PathVariable Long id) {
        return sondageMapper.toResponse(sondageService.nouvelleVersion(moi.id(), id));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize(ROLES)
    @Operation(summary = "Versions du sondage", description = "Toute la lignée, de la plus ancienne à la plus récente.")
    public List<VersionResponse> versions(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                          @PathVariable Long id) {
        return sondageMapper.toVersionResponses(sondageService.versions(moi.id(), id));
    }
}
