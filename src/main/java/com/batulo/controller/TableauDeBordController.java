package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.TableauDeBordMapper;
import com.batulo.service.TableauDeBordService;
import com.batulo.service.model.TableauDeBordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tableau de bord de l'admin, sur l'entreprise du jeton. Règles d'abonnement habituelles
 * (AccesAbonnementInterceptor) : lisible abonnement expiré, refusé en attente de paiement ou entreprise désactivée.
 */
@RestController
@RequestMapping("/api/admin/tableau-de-bord")
@Tag(name = "Admin – tableau de bord", description = "Synthèse de l'entreprise de l'admin")
public class TableauDeBordController {

    private final TableauDeBordService tableauDeBordService;
    private final TableauDeBordMapper tableauDeBordMapper;

    public TableauDeBordController(TableauDeBordService tableauDeBordService, TableauDeBordMapper tableauDeBordMapper) {
        this.tableauDeBordService = tableauDeBordService;
        this.tableauDeBordMapper = tableauDeBordMapper;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tableau de bord de l'admin",
            description = "Abonnement (formule, statut recalculé, date d'expiration, jours restants ; null s'il n'y "
                    + "en a pas), gestionnaires actifs et désactivés, sondages par statut et nombre total de réponses. "
                    + "Un sondage = une lignée, compté au statut de sa dernière version publiée en cours ou terminée, "
                    + "sinon de son brouillon (même ligne que GET /api/sondages?derniereVersion=true).")
    public TableauDeBordResponse tableauDeBord(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi) {
        return tableauDeBordMapper.toResponse(tableauDeBordService.tableauDeBord(moi.entrepriseId()));
    }
}
