package com.batulo.service;

import com.batulo.enums.FormatExport;
import com.batulo.enums.GranulariteDate;
import com.batulo.enums.SeparateurDecimal;
import com.batulo.enums.ValeursExport;
import com.batulo.service.model.ExportModel;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Exports et rapports (module 4.4), avec les mêmes droits et filtres que les statistiques (version ou lignée,
 * période, critères). Les droits et les paramètres sont vérifiés avant d'écrire le moindre octet.
 *
 * @see StatistiqueService
 */
public interface ExportService {

    /**
     * Réponses brutes en CSV ou XLSX, une ligne par réponse, lues en flux.
     *
     * @throws com.batulo.exception.RegleGestionException FORMAT_INVALIDE (PDF), CRITERE_INVALIDE, PERIODE_INVALIDE
     */
    ExportModel exporterReponses(Long utilisateurId, Long sondageId, FormatExport format, ValeursExport valeurs,
                                 SeparateurDecimal separateurDecimal, boolean lignee, LocalDate du, LocalDate au,
                                 Map<String, List<String>> criteres);

    /** Rapport des statistiques en PDF (avec graphiques) ou XLSX (résumé + une feuille par question). */
    ExportModel exporterStatistiques(Long utilisateurId, Long sondageId, FormatExport format, boolean lignee,
                                     LocalDate du, LocalDate au, GranulariteDate granulariteDate,
                                     Map<String, List<String>> criteres);

    /** Tableau croisé en PDF ou XLSX. */
    ExportModel exporterCroisement(Long utilisateurId, Long sondageId, FormatExport format, String cleQuestion,
                                   String clePar, boolean lignee, LocalDate du, LocalDate au,
                                   Map<String, List<String>> criteres);
}
