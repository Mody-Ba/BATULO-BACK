package com.batulo.controller;

import com.batulo.mappers.NotificationMapper;
import com.batulo.service.ExpirationAbonnementService;
import com.batulo.service.model.ResultatTacheResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Route de DÉVELOPPEMENT, créée uniquement avec le profil dev : lance la tâche quotidienne à la main. */
@RestController
@Profile("dev")
@RequestMapping("/api/dev/taches")
@Tag(name = "Développement (profil dev)", description = "Outils de test, absents en production")
public class DevTacheController {

    private final ExpirationAbonnementService expirationAbonnementService;
    private final NotificationMapper notificationMapper;

    public DevTacheController(ExpirationAbonnementService expirationAbonnementService,
                              NotificationMapper notificationMapper) {
        this.expirationAbonnementService = expirationAbonnementService;
        this.notificationMapper = notificationMapper;
    }

    @PostMapping("/abonnements")
    @Operation(summary = "Lancer la tâche des abonnements maintenant (dev)",
            description = "Même traitement que le passage de 8 h : rappels à 7 jours ou moins et expirations. "
                    + "Relançable sans risque : un avis n'est jamais envoyé deux fois pour la même période.")
    public ResultatTacheResponse lancer() {
        return notificationMapper.toResponse(expirationAbonnementService.verifierAbonnements());
    }
}
