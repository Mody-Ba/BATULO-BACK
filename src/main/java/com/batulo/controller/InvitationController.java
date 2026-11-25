package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.enums.StatutInvitation;
import com.batulo.mappers.InvitationMapper;
import com.batulo.service.InvitationService;
import com.batulo.service.model.EnvoiInvitationsRequest;
import com.batulo.service.model.InvitationRequest;
import com.batulo.service.model.InvitationResponse;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.RapportEnvoiResponse;
import com.batulo.service.model.RapportImportResponse;
import com.batulo.service.model.RepriseInvitationsRequest;
import com.batulo.service.model.ResumeInvitationsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Invitations nominatives (phase 4, étape 4) : liste des invités d'un sondage, liens personnels à usage unique envoyés
 * par courriel, relances et suivi. La liste porte sur le sondage (toutes ses versions) : {id} est n'importe quelle
 * version. Accès : l'admin, ou le gestionnaire créateur ou assigné ; sinon 404.
 */
@RestController
@Validated
@RequestMapping("/api/sondages/{id}/invitations")
@Tag(name = "Sondages – invitations", description = "Invités, liens personnels à usage unique, envois, relances, suivi")
public class InvitationController {

    private static final String ROLES = "hasAnyRole('ADMIN', 'GESTIONNAIRE')";

    private final InvitationService invitationService;
    private final InvitationMapper invitationMapper;

    public InvitationController(InvitationService invitationService, InvitationMapper invitationMapper) {
        this.invitationService = invitationService;
        this.invitationMapper = invitationMapper;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    @Operation(summary = "Invités et suivi",
            description = "Paginé par ordre d'ajout. Statut : A_INVITER, INVITEE, OUVERTE (formulaire ouvert avec le lien), "
                    + "A_REPONDU, REVOQUEE, EN_ECHEC (dernier courriel refusé). reponduLe est arrondi au jour si les "
                    + "réponses sont anonymes. Le lien personnel n'est jamais renvoyé.")
    public PageResponse<InvitationResponse> lister(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id,
            @RequestParam(required = false) StatutInvitation statut,
            @Parameter(description = "Prénom, nom ou courriel (sans majuscules ni accents)")
            @RequestParam(required = false) String recherche,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return invitationMapper.toResponse(invitationService.lister(moi.id(), id, statut, recherche, page, taille));
    }

    @GetMapping("/resume")
    @PreAuthorize(ROLES)
    @Operation(summary = "Compteurs du suivi",
            description = "Nombre d'invités par statut, invitations envoyées et taux de réponse (répondus / envoyées).")
    public ResumeInvitationsResponse resume(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                            @PathVariable Long id) {
        return invitationMapper.toResponse(invitationService.resume(moi.id(), id));
    }

    @PostMapping
    @PreAuthorize(ROLES)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un invité",
            description = "Possible dès le brouillon. 400 INVITE_INVALIDE, 409 INVITE_EXISTANT (courriel déjà invité), "
                    + "400 LANGUE_INVALIDE, 400 LIMITE_ATTEINTE (10 000 invités).")
    public InvitationResponse ajouter(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                      @PathVariable Long id, @Valid @RequestBody InvitationRequest request) {
        return invitationMapper.toResponse(invitationService.ajouter(moi.id(), id, invitationMapper.toModel(request)));
    }

    @PutMapping("/{invitationId}")
    @PreAuthorize(ROLES)
    @Operation(summary = "Modifier un invité",
            description = "Le lien personnel ne change pas. 409 INVITE_A_REPONDU, INVITATION_REVOQUEE, INVITE_EXISTANT.")
    public InvitationResponse modifier(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                       @PathVariable Long id, @PathVariable Long invitationId,
                                       @Valid @RequestBody InvitationRequest request) {
        return invitationMapper.toResponse(invitationService.modifier(moi.id(), id, invitationId,
                invitationMapper.toModel(request)));
    }

    @DeleteMapping("/{invitationId}")
    @PreAuthorize(ROLES)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un invité", description = "Tant qu'il n'a pas répondu (409 INVITE_A_REPONDU).")
    public void supprimer(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                          @PathVariable Long id, @PathVariable Long invitationId) {
        invitationService.supprimer(moi.id(), id, invitationId);
    }

    @PostMapping("/{invitationId}/revoquer")
    @PreAuthorize(ROLES)
    @Operation(summary = "Révoquer une invitation",
            description = "Définitif : le lien personnel ne marche plus (410 INVITATION_REVOQUEE). 409 INVITE_A_REPONDU.")
    public InvitationResponse revoquer(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                       @PathVariable Long id, @PathVariable Long invitationId) {
        return invitationMapper.toResponse(invitationService.revoquer(moi.id(), id, invitationId));
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(ROLES)
    @Operation(summary = "Importer des invités (CSV)",
            description = "UTF-8 (BOM accepté), séparateur « , » ou « ; » détecté sur l'en-tête. En-têtes (ordre libre, "
                    + "sans majuscules ni accents) : prenom, nom, courriel (obligatoire), telephone, langue (fr, en, ar). "
                    + "1000 lignes au plus (400 IMPORT_TROP_GRAND). Rapport : lignes acceptées et refusées avec la raison "
                    + "(COURRIEL_MANQUANT, COURRIEL_INVALIDE, DOUBLON_FICHIER, DEJA_INVITE, LANGUE_INVALIDE, "
                    + "TEXTE_TROP_LONG, TELEPHONE_INVALIDE, LIMITE_INVITES).")
    public RapportImportResponse importer(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                          @PathVariable Long id, @RequestPart("fichier") MultipartFile fichier)
            throws IOException {
        return invitationMapper.toResponse(invitationService.importer(moi.id(), id, fichier.getBytes()));
    }

    @PostMapping("/reprendre")
    @PreAuthorize(ROLES)
    @Operation(summary = "Reprendre la liste d'un autre sondage",
            description = "Copie l'identité des invités (prénom, nom, courriel, téléphone, langue) d'un sondage visible "
                    + "de l'entreprise, sans statut ni lien ; adresses déjà invitées ignorées. Utile après une "
                    + "duplication ou une réutilisation (les nouvelles versions partagent déjà la liste).")
    public RapportImportResponse reprendre(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                           @PathVariable Long id, @Valid @RequestBody RepriseInvitationsRequest request) {
        return invitationMapper.toResponse(invitationService.reprendre(moi.id(), id, request.sondageSourceId()));
    }

    @PostMapping("/envoyer")
    @PreAuthorize(ROLES)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Envoyer les invitations par courriel",
            description = "En arrière-plan (202) : la sélection, ou sans liste tous ceux qui n'ont jamais été invités. "
                    + "Courriel dans la langue de l'invité avec le titre, l'entreprise, le lien personnel et la date de "
                    + "fermeture. Sondage publié, ouvert ou planifié (409 SONDAGE_NON_OUVERT, SONDAGE_FERME). Limite par "
                    + "heure et par entreprise : 429 LIMITE_ENVOIS. Ignorées : INTROUVABLE, REVOQUEE, A_REPONDU, "
                    + "DEJA_INVITEE (utiliser la relance), ENVOI_EN_COURS.")
    public RapportEnvoiResponse envoyer(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                        @PathVariable Long id,
                                        @Valid @RequestBody(required = false) EnvoiInvitationsRequest request) {
        return invitationMapper.toResponse(invitationService.envoyer(moi.id(), id,
                request == null ? null : request.invitationIds()));
    }

    @PostMapping("/relancer")
    @PreAuthorize(ROLES)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Relancer les invités sans réponse",
            description = "En arrière-plan (202) : la sélection, ou tous les invités sans réponse. Une relance au plus "
                    + "toutes les 24 h par personne (ignorée : DELAI_RELANCE) ; JAMAIS_INVITEE pour un invité jamais "
                    + "invité. Mêmes conditions et limite que l'envoi.")
    public RapportEnvoiResponse relancer(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                         @PathVariable Long id,
                                         @Valid @RequestBody(required = false) EnvoiInvitationsRequest request) {
        return invitationMapper.toResponse(invitationService.relancer(moi.id(), id,
                request == null ? null : request.invitationIds()));
    }

    @GetMapping("/export")
    @PreAuthorize(ROLES)
    @Operation(summary = "Exporter le suivi (CSV)",
            description = "Une ligne par invité : identité, statut et dates (UTF-8 avec BOM, « ; »). « Répondu le » au jour.")
    public void exporter(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                         @PathVariable Long id, HttpServletResponse response) throws IOException {
        ExportController.envoyer(invitationService.exporter(moi.id(), id), response);
    }
}
