package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.mappers.CollecteMapper;
import com.batulo.service.SuiviCollecteService;
import com.batulo.service.model.LienSondageResponse;
import com.batulo.service.model.PageResponse;
import com.batulo.service.model.ReponseResponse;
import com.batulo.service.model.SuiviCollecteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lien public, QR code et suivi de la collecte (module 4.2), pour l'admin et les gestionnaires qui voient le
 * sondage. Lecture seule : permis avec un abonnement expiré.
 */
@RestController
@RequestMapping("/api/sondages/{id}")
@Tag(name = "Sondages – collecte", description = "Lien, QR code et suivi des réponses")
public class CollecteController {

    private static final String ROLES = "hasAnyRole('ADMIN', 'GESTIONNAIRE')";

    private final SuiviCollecteService suiviCollecteService;
    private final CollecteMapper collecteMapper;

    public CollecteController(SuiviCollecteService suiviCollecteService, CollecteMapper collecteMapper) {
        this.suiviCollecteService = suiviCollecteService;
        this.collecteMapper = collecteMapper;
    }

    @GetMapping("/lien")
    @PreAuthorize(ROLES)
    @Operation(summary = "Lien public du sondage",
            description = "Le même pour toutes les versions : il mène toujours à la version ouverte. "
                    + "409 SONDAGE_BROUILLON tant que le sondage n'a jamais été publié.")
    public LienSondageResponse lien(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                    @PathVariable Long id) {
        return collecteMapper.toResponse(suiviCollecteService.lien(moi.id(), id));
    }

    @GetMapping(value = "/qr-code", produces = MediaType.IMAGE_PNG_VALUE)
    @PreAuthorize(ROLES)
    @Operation(summary = "QR code du lien public (image PNG)")
    public ResponseEntity<byte[]> qrCode(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                         @PathVariable Long id,
                                         @RequestParam(defaultValue = "300")
                                         @Min(value = 100, message = "taille doit être au moins 100")
                                         @Max(value = 1000, message = "taille ne doit pas dépasser 1000") int taille) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .body(suiviCollecteService.qrCode(moi.id(), id, taille));
    }

    @GetMapping("/collecte")
    @PreAuthorize(ROLES)
    @Operation(summary = "Suivi de la collecte",
            description = "Nombre de réponses (cette version et toutes les versions), date de la dernière, collecte "
                    + "ouverte ou non et pourquoi.")
    public SuiviCollecteResponse suivi(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                                       @PathVariable Long id) {
        return collecteMapper.toResponse(suiviCollecteService.suivi(moi.id(), id));
    }

    @GetMapping("/reponses")
    @PreAuthorize(ROLES)
    @Operation(summary = "Réponses reçues (cette version)", description = "Paginé, les plus récentes d'abord.")
    public PageResponse<ReponseResponse> reponses(
            @Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "taille doit être au moins 1")
            @Max(value = 100, message = "taille ne doit pas dépasser 100") int taille) {
        return collecteMapper.toResponse(suiviCollecteService.reponses(moi.id(), id, page, taille));
    }
}
