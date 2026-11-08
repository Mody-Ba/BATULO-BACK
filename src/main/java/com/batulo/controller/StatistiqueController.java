package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.enums.GranulariteDate;
import com.batulo.mappers.StatistiqueMapper;
import com.batulo.service.StatistiqueService;
import com.batulo.service.model.StatistiquesResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Statistiques de base d'un sondage (module 4.3, AD-13, GE-05) : admin pour tous les sondages de l'entreprise,
 * gestionnaire pour ceux qu'il a créés ou qui lui sont assignés (RG-02). Lecture seule : permis avec un abonnement
 * expiré. Les graphiques sont dessinés par le frontend à partir de ces données.
 */
@RestController
@RequestMapping("/api/sondages/{id}/statistiques")
@Tag(name = "Sondages – statistiques", description = "Fréquences, pourcentages, moyennes et distributions (module 4.3)")
public class StatistiqueController {

    private static final String ROLES = "hasAnyRole('ADMIN', 'GESTIONNAIRE')";

    private final StatistiqueService statistiqueService;
    private final StatistiqueMapper statistiqueMapper;

    public StatistiqueController(StatistiqueService statistiqueService, StatistiqueMapper statistiqueMapper) {
        this.statistiqueService = statistiqueService;
        this.statistiqueMapper = statistiqueMapper;
    }

    @GetMapping
    @PreAuthorize(ROLES)
    @Operation(summary = "Statistiques du sondage",
            description = "Résumé, puis pour chaque question le bloc de son type : options (choix, oui/non), numerique "
                    + "(échelle, numérique), dates, classement, matrice ou textes (1re page). Pourcentages sur les "
                    + "répondants à la question ; sansReponse à part. lignee=true cumule toutes les versions.")
    public StatistiquesResponse statistiques(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long id,
            @Parameter(description = "true : toutes les versions du sondage ; false (défaut) : cette version")
            @RequestParam(defaultValue = "false") boolean lignee,
            @Parameter(description = "Facultatif, inclus (AAAA-MM-JJ)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @Parameter(description = "Facultatif, inclus (AAAA-MM-JJ)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @Parameter(description = "Facultatif : JOUR ou MOIS pour les questions DATE (automatique sinon)")
            @RequestParam(required = false) GranulariteDate granulariteDate) {
        return statistiqueMapper.toResponse(
                statistiqueService.statistiques(moi.id(), id, lignee, du, au, granulariteDate));
    }

    @GetMapping("/questions/{questionId}/textes")
    @PreAuthorize(ROLES)
    @Operation(summary = "Textes d'une question à texte libre",
            description = "Paginé, les plus récents d'abord ; mêmes filtres que les statistiques.")
    public StatistiquesResponse.PageTextes textes(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long id,
            @PathVariable Long questionId,
            @RequestParam(defaultValue = "false") boolean lignee,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return statistiqueMapper.toResponse(
                statistiqueService.textes(moi.id(), id, questionId, lignee, du, au, page, taille));
    }
}
