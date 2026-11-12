package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.MessagerieMapper;
import com.batulo.mappers.StatistiqueMapper;
import com.batulo.service.EnvoiStatistiqueService;
import com.batulo.service.model.EnvoiStatistiqueRequest;
import com.batulo.service.model.EnvoiStatistiqueResponse;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.StatistiquesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/** Envoi des statistiques d'un sondage terminé par un gestionnaire à son admin (GE-06, RG-04). */
@RestController
@Validated
@Tag(name = "Statistiques envoyées", description = "Le gestionnaire envoie les statistiques d'un sondage terminé à son admin")
public class EnvoiStatistiqueController {

    private final EnvoiStatistiqueService envoiService;
    private final MessagerieMapper messagerieMapper;
    private final StatistiqueMapper statistiqueMapper;

    public EnvoiStatistiqueController(EnvoiStatistiqueService envoiService, MessagerieMapper messagerieMapper,
                                      StatistiqueMapper statistiqueMapper) {
        this.envoiService = envoiService;
        this.messagerieMapper = messagerieMapper;
        this.statistiqueMapper = statistiqueMapper;
    }

    @PostMapping("/api/sondages/{id}/envois-statistiques")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('GESTIONNAIRE')")
    @Operation(summary = "Envoyer les statistiques à mon admin",
            description = "Gestionnaire créateur ou assigné ; sondage TERMINE (en lignée : toutes ses versions publiées), "
                    + "sinon 409 SONDAGE_NON_TERMINE. Filtres comme GET /statistiques. Un message est posté dans la "
                    + "conversation avec l'admin, qui reçoit une notification. Autorisé avec un abonnement expiré.")
    public EnvoiStatistiqueResponse envoyer(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                            @PathVariable Long id,
                                            @Valid @RequestBody EnvoiStatistiqueRequest request) {
        return messagerieMapper.toResponse(envoiService.envoyer(moi.id(), id, messagerieMapper.toModel(request)));
    }

    @GetMapping("/api/statistiques-envoyees")
    @PreAuthorize("hasRole('GESTIONNAIRE')")
    @Operation(summary = "Mes envois de statistiques", description = "Les plus récents d'abord.")
    public PageResponse<EnvoiStatistiqueResponse> envoyees(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return messagerieMapper.toResponse(envoiService.envoyees(moi.id(), page, taille), messagerieMapper::toResponse);
    }

    @GetMapping("/api/statistiques-recues")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Statistiques reçues", description = "Envoyées par les gestionnaires, avec leur nom ; "
            + "consulteLe null : pas encore ouvertes.")
    public PageResponse<EnvoiStatistiqueResponse> recues(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return messagerieMapper.toResponse(envoiService.recues(moi.id(), page, taille), messagerieMapper::toResponse);
    }

    @GetMapping("/api/statistiques-recues/{envoiId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Ouvrir des statistiques reçues",
            description = "Recalculées avec les filtres de l'envoi (un sondage terminé ne reçoit plus de réponses).")
    public StatistiquesResponse statistiques(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                             @PathVariable Long envoiId) {
        return statistiqueMapper.toResponse(envoiService.statistiques(moi.id(), envoiId));
    }

    @GetMapping("/api/statistiques-recues/{envoiId}/pdf")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Rapport PDF de statistiques reçues", description = "Même rapport que l'export de l'étape 3.3.")
    public void pdf(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                    @PathVariable Long envoiId, HttpServletResponse response) throws IOException {
        ExportController.envoyer(envoiService.pdf(moi.id(), envoiId), response);
    }
}
