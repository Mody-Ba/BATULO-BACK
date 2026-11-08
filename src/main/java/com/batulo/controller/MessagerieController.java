package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.MessagerieMapper;
import com.batulo.service.MessagerieService;
import com.batulo.service.model.ConversationResponse;
import com.batulo.service.model.MessageInterneResponse;
import com.batulo.service.model.MessageRequest;
import com.batulo.service.model.NombreNonLuesResponse;
import com.batulo.service.model.NotificationsMarqueesResponse;
import com.batulo.service.model.OuvertureConversationRequest;
import com.batulo.service.model.PageResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Messagerie interne (AD-14, GE-07, SA-07) : super admins <-> admin, admin <-> ses gestionnaires. Texte brut, sans
 * pièce jointe ni suppression. Une conversation inaccessible donne 404.
 */
@RestController
@Validated
@RequestMapping("/api/messagerie")
@Tag(name = "Messagerie", description = "Conversations à deux : super admins (Batulo) <-> admin, admin <-> gestionnaires")
public class MessagerieController {

    private final MessagerieService messagerieService;
    private final MessagerieMapper messagerieMapper;

    public MessagerieController(MessagerieService messagerieService, MessagerieMapper messagerieMapper) {
        this.messagerieService = messagerieService;
        this.messagerieMapper = messagerieMapper;
    }

    @PostMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Ouvrir (ou retrouver) une conversation",
            description = "Admin : interlocuteurId = un de ses gestionnaires, ou absent pour écrire à Batulo. "
                    + "Gestionnaire : corps vide (son admin). Super admin : interlocuteurId = un admin. "
                    + "Sinon 404 INTERLOCUTEUR_INTROUVABLE.")
    public ConversationResponse ouvrir(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                       @RequestBody(required = false) OuvertureConversationRequest request) {
        return messagerieMapper.toResponse(messagerieService.ouvrir(moi.id(),
                request == null ? null : request.interlocuteurId()));
    }

    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mes conversations",
            description = "La plus récemment active d'abord, avec le dernier message et le nombre de non lus. "
                    + "Super admin : toutes les conversations avec Batulo (boîte partagée).")
    public PageResponse<ConversationResponse> conversations(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return messagerieMapper.toResponse(messagerieService.conversations(moi.id(), page, taille),
                messagerieMapper::toResponse);
    }

    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Messages d'une conversation", description = "Les plus récents d'abord. Ne les marque pas "
            + "comme lus : POST .../lu.")
    public PageResponse<MessageInterneResponse> messages(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "30") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return messagerieMapper.toResponse(messagerieService.messages(moi.id(), id, page, taille),
                messagerieMapper::toResponse);
    }

    @PostMapping("/conversations/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Envoyer un message", description = "Le destinataire reçoit une notification « Nouveau "
            + "message » pour le premier message non lu de la conversation. Abonnement non actif : la conversation "
            + "admin <-> gestionnaire est en lecture seule (403 ABONNEMENT_EXPIRE) ; celle avec Batulo reste ouverte.")
    public MessageInterneResponse envoyer(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                          @PathVariable Long id, @Valid @RequestBody MessageRequest request) {
        return messagerieMapper.toResponse(messagerieService.envoyer(moi.id(), id, request.contenu()));
    }

    @PostMapping("/conversations/{id}/lu")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Marquer la conversation comme lue", description = "Les messages reçus seulement.")
    public NotificationsMarqueesResponse marquerLu(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id) {
        int nombre = messagerieService.marquerLu(moi.id(), id);
        return new NotificationsMarqueesResponse(nombre + " message(s) marqué(s) comme lu(s).", nombre);
    }

    @GetMapping("/non-lus")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Nombre total de messages non lus")
    public NombreNonLuesResponse nonLus(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi) {
        return messagerieMapper.toNonLus(messagerieService.nonLus(moi.id()));
    }
}
