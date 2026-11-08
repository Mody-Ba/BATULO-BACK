package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.service.DevReponsesService;
import com.batulo.service.model.MessageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Route de DÉVELOPPEMENT, créée uniquement avec le profil dev : jeux de réponses pour tester les statistiques. */
@RestController
@Profile("dev")
@RequestMapping("/api/dev/sondages/{id}")
@Tag(name = "Développement (profil dev)", description = "Outils de test, absents en production")
public class DevSondageController {

    private final DevReponsesService devReponsesService;

    public DevSondageController(DevReponsesService devReponsesService) {
        this.devReponsesService = devReponsesService;
    }

    @PostMapping("/reponses-aleatoires")
    @PreAuthorize("hasAnyRole('ADMIN', 'GESTIONNAIRE')")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Générer des réponses aléatoires valides (dev)",
            description = "Sur un sondage EN_COURS que vous voyez : réponses valides (types, configuration, questions "
                    + "obligatoires), datées sur les 30 derniers jours. 409 SONDAGE_NON_EN_COURS sinon.")
    public MessageResponse genererReponses(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                           @PathVariable Long id,
                                           @RequestParam(defaultValue = "50")
                                           @Min(value = 1, message = "nombre doit être au moins 1")
                                           @Max(value = 1000, message = "nombre ne doit pas dépasser 1000") int nombre) {
        int crees = devReponsesService.genererReponses(moi.id(), id, nombre);
        return new MessageResponse(crees + " réponses de test générées.");
    }
}
