package com.batulo.controller;

import com.batulo.configs.security.UtilisateurConnecte;
import com.batulo.enums.FormatExport;
import com.batulo.enums.GranulariteDate;
import com.batulo.enums.SeparateurDecimal;
import com.batulo.enums.ValeursExport;
import com.batulo.mappers.StatistiqueMapper;
import com.batulo.service.ExportService;
import com.batulo.service.model.ExportModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Exports et rapports (module 4.4) : réponses brutes (CSV, XLSX), statistiques (PDF, XLSX) et tableau croisé.
 * Mêmes droits et mêmes filtres que les statistiques. Le fichier est écrit en flux dans la réponse HTTP, après
 * vérification des droits et des paramètres (une erreur arrive donc toujours en JSON, avant le fichier).
 */
@RestController
@RequestMapping("/api/sondages/{id}/export")
@Tag(name = "Sondages – exports", description = "CSV, Excel et PDF (module 4.4)")
public class ExportController {

    private static final String ROLES = "hasAnyRole('ADMIN', 'GESTIONNAIRE')";

    private final ExportService exportService;
    private final StatistiqueMapper statistiqueMapper;

    public ExportController(ExportService exportService, StatistiqueMapper statistiqueMapper) {
        this.exportService = exportService;
        this.statistiqueMapper = statistiqueMapper;
    }

    @GetMapping("/reponses")
    @PreAuthorize(ROLES)
    @Operation(summary = "Exporter les réponses brutes (CSV ou XLSX)",
            description = "Une ligne par réponse (Id, Date, Version), une colonne par question (libellé). Choix multiple "
                    + "et classement : une colonne par option (1/0, ou rang) ; matrice : une colonne par ligne ; vide = "
                    + "pas de réponse. CSV en UTF-8 avec BOM : « ; » et virgule décimale (Excel francophone), ou « , » et "
                    + "point décimal avec separateurDecimal=POINT (Python, R). Écrit en flux, sans limite de volume.")
    public void reponses(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                         @PathVariable Long id,
                         @Parameter(description = "CSV ou XLSX") @RequestParam FormatExport format,
                         @Parameter(description = "LIBELLES (défaut) ou CODES (valeurs des options, 1/0 pour oui/non)")
                         @RequestParam(defaultValue = "LIBELLES") ValeursExport valeurs,
                         @Parameter(description = "CSV : VIRGULE (défaut, « ; ») ou POINT (« , »)")
                         @RequestParam(defaultValue = "VIRGULE") SeparateurDecimal separateurDecimal,
                         @RequestParam(defaultValue = "false") boolean lignee,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                         @Parameter(description = "Filtres de segmentation, répétables : cleDuCritere:valeur")
                         @RequestParam(required = false) List<String> critere,
                         HttpServletResponse response) throws IOException {
        envoyer(exportService.exporterReponses(moi.id(), id, format, valeurs, separateurDecimal, lignee, du, au,
                statistiqueMapper.toCriteres(critere)), response);
    }

    @GetMapping("/statistiques")
    @PreAuthorize(ROLES)
    @Operation(summary = "Rapport des statistiques (PDF ou XLSX)",
            description = "PDF : page de titre (sondage, entreprise, période, critères, réponses) puis une section par "
                    + "question avec tableau et barres. XLSX : feuille Résumé + une feuille par question. Mêmes filtres "
                    + "que GET /statistiques.")
    public void statistiques(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                             @PathVariable Long id,
                             @Parameter(description = "PDF ou XLSX") @RequestParam FormatExport format,
                             @RequestParam(defaultValue = "false") boolean lignee,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                             @RequestParam(required = false) GranulariteDate granulariteDate,
                             @RequestParam(required = false) List<String> critere,
                             HttpServletResponse response) throws IOException {
        envoyer(exportService.exporterStatistiques(moi.id(), id, format, lignee, du, au, granulariteDate,
                statistiqueMapper.toCriteres(critere)), response);
    }

    @GetMapping("/croisement")
    @PreAuthorize(ROLES)
    @Operation(summary = "Tableau croisé (PDF ou XLSX)",
            description = "Mêmes paramètres que GET /statistiques/croisement : question et par (clés).")
    public void croisement(@Parameter(hidden = true) @AuthenticationPrincipal UtilisateurConnecte moi,
                           @PathVariable Long id,
                           @Parameter(description = "PDF ou XLSX") @RequestParam FormatExport format,
                           @RequestParam String question,
                           @RequestParam String par,
                           @RequestParam(defaultValue = "false") boolean lignee,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate du,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate au,
                           @RequestParam(required = false) List<String> critere,
                           HttpServletResponse response) throws IOException {
        envoyer(exportService.exporterCroisement(moi.id(), id, format, question, par, lignee, du, au,
                statistiqueMapper.toCriteres(critere)), response);
    }

    /** En-têtes (nom de fichier, type, pas de cache : ce sont des réponses) puis contenu, écrit en flux. */
    static void envoyer(ExportModel export, HttpServletResponse response) throws IOException {
        String nom = export.nomFichier();
        response.setContentType(export.typeContenu());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nom + "\"; filename*=UTF-8''"
                + URLEncoder.encode(nom, StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        export.contenu().ecrire(response.getOutputStream());
        response.flushBuffer();
    }
}
