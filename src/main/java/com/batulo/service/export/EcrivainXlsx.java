package com.batulo.service.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Excel (.xlsx) écrit en flux avec SXSSF : seules 100 lignes restent en mémoire, le reste part dans un fichier
 * temporaire compressé, supprimé à la fin. Cellules typées (nombres, dates), en-tête en gras et figé, filtre
 * automatique. Au-delà de la limite d'Excel (1 048 576 lignes), la suite va sur une nouvelle feuille.
 * Les textes sont écrits comme du texte : jamais interprétés comme des formules.
 */
public class EcrivainXlsx implements EcrivainTableau {

    static final int LIGNES_EN_MEMOIRE = 100;
    static final int LIGNES_MAX_PAR_FEUILLE = 1_048_575;
    private static final int LARGEUR_MAX = 60;

    private final OutputStream flux;
    private final String nomFeuille;
    private final SXSSFWorkbook classeur;
    private final CellStyle styleEntete;
    private final CellStyle styleDate;
    private final CellStyle styleDateHeure;
    private List<String> entetes;
    private SXSSFSheet feuille;
    private int numeroFeuille;
    private int ligneCourante;

    public EcrivainXlsx(OutputStream flux, String nomFeuille) {
        this.flux = flux;
        this.nomFeuille = nomFeuille;
        this.classeur = new SXSSFWorkbook(LIGNES_EN_MEMOIRE);
        classeur.setCompressTempFiles(true);
        Font gras = classeur.createFont();
        gras.setBold(true);
        styleEntete = classeur.createCellStyle();
        styleEntete.setFont(gras);
        styleDate = classeur.createCellStyle();
        styleDate.setDataFormat(classeur.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));
        styleDateHeure = classeur.createCellStyle();
        styleDateHeure.setDataFormat(classeur.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
    }

    @Override
    public void entetes(List<String> entetes) {
        this.entetes = List.copyOf(entetes);
        nouvelleFeuille();
    }

    @Override
    public void ligne(List<Object> valeurs) {
        if (ligneCourante > LIGNES_MAX_PAR_FEUILLE) {
            nouvelleFeuille();
        }
        Row ligne = feuille.createRow(ligneCourante++);
        for (int i = 0; i < valeurs.size(); i++) {
            Object valeur = valeurs.get(i);
            if (valeur == null) {
                continue;
            }
            Cell cellule = ligne.createCell(i);
            if (valeur instanceof BigDecimal nombre) {
                cellule.setCellValue(nombre.doubleValue());
            } else if (valeur instanceof Number nombre) {
                cellule.setCellValue(nombre.doubleValue());
            } else if (valeur instanceof LocalDate date) {
                cellule.setCellValue(date);
                cellule.setCellStyle(styleDate);
            } else if (valeur instanceof LocalDateTime dateHeure) {
                cellule.setCellValue(dateHeure);
                cellule.setCellStyle(styleDateHeure);
            } else {
                cellule.setCellValue(valeur.toString());
            }
        }
    }

    @Override
    public void close() throws IOException {
        terminerFeuille();
        try {
            classeur.write(flux);
            flux.flush();
        } finally {
            classeur.dispose();
            classeur.close();
        }
    }

    private void nouvelleFeuille() {
        terminerFeuille();
        numeroFeuille++;
        feuille = classeur.createSheet(numeroFeuille == 1 ? nomFeuille : nomFeuille + " (" + numeroFeuille + ")");
        Row ligne = feuille.createRow(0);
        for (int i = 0; i < entetes.size(); i++) {
            Cell cellule = ligne.createCell(i);
            cellule.setCellValue(entetes.get(i));
            cellule.setCellStyle(styleEntete);
            feuille.setColumnWidth(i, Math.min(LARGEUR_MAX, Math.max(10, entetes.get(i).length() + 2)) * 256);
        }
        feuille.createFreezePane(0, 1);
        ligneCourante = 1;
    }

    private void terminerFeuille() {
        if (feuille != null && entetes != null && !entetes.isEmpty()) {
            feuille.setAutoFilter(new CellRangeAddress(0, Math.max(0, ligneCourante - 1), 0, entetes.size() - 1));
        }
    }
}
