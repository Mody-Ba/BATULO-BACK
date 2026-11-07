package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.NotificationMapper;
import com.batulo.service.NotificationService;
import com.batulo.service.model.NombreNonLuesResponse;
import com.batulo.service.model.NotificationResponse;
import com.batulo.service.model.NotificationsMarqueesResponse;
import com.batulo.service.model.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Notifications du tableau de bord de l'utilisateur du jeton (tout rôle connecté). */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Notifications du tableau de bord (SA-05, AD-04)")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    public NotificationController(NotificationService notificationService, NotificationMapper notificationMapper) {
        this.notificationService = notificationService;
        this.notificationMapper = notificationMapper;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mes notifications", description = "Paginé, les plus récentes d'abord.")
    public PageResponse<NotificationResponse> lister(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return notificationMapper.toResponse(notificationService.lister(moi.id(), page, taille));
    }

    @GetMapping("/non-lues/nombre")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Nombre de notifications non lues")
    public NombreNonLuesResponse nombreNonLues(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi) {
        return notificationMapper.toNombreNonLuesResponse(notificationService.nombreNonLues(moi.id()));
    }

    @PatchMapping("/{id}/lue")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Marquer une notification comme lue",
            description = "404 si la notification n'appartient pas à l'utilisateur.")
    public NotificationResponse marquerLue(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long id) {
        return notificationMapper.toResponse(notificationService.marquerLue(moi.id(), id));
    }

    @PatchMapping("/lues")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Tout marquer comme lu")
    public NotificationsMarqueesResponse toutMarquerLu(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi) {
        return notificationMapper.toMarqueesResponse(notificationService.toutMarquerLu(moi.id()));
    }
}
