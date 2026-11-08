package com.batulo.service.export;

import com.batulo.enums.SeparateurDecimal;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * CSV en UTF-8 avec BOM (Excel reconnaît l'encodage), selon RFC 4180 : guillemets si la valeur contient le
 * séparateur, un guillemet ou un retour à la ligne. VIRGULE : « ; » et décimales à virgule (Excel francophone) ;
 * POINT : « , » et décimales à point (Python, R).
 * <p>
 * Protection contre l'injection de formules (OWASP) : un texte qui commence par =, +, -, @, tabulation ou retour
 * chariot est précédé d'une apostrophe, pour qu'Excel ne l'exécute jamais. Les nombres ne sont pas concernés.
 */
public class EcrivainCsv implements EcrivainTableau {

    private static final char[] DEBUT_FORMULE = {'=', '+', '-', '@', '\t', '\r'};
    private static final DateTimeFormatter DATE_HEURE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Writer sortie;
    private final SeparateurDecimal decimal;
    private final char separateur;

    public EcrivainCsv(OutputStream flux, SeparateurDecimal decimal) throws IOException {
        this.sortie = new BufferedWriter(new OutputStreamWriter(flux, StandardCharsets.UTF_8), 64 * 1024);
        this.decimal = decimal;
        this.separateur = decimal == SeparateurDecimal.VIRGULE ? ';' : ',';
        sortie.write('﻿');
    }

    @Override
    public void entetes(List<String> entetes) throws IOException {
        ligne(List.copyOf(entetes));
    }

    @Override
    public void ligne(List<Object> valeurs) throws IOException {
        for (int i = 0; i < valeurs.size(); i++) {
            if (i > 0) {
                sortie.write(separateur);
            }
            sortie.write(cellule(valeurs.get(i)));
        }
        sortie.write("\r\n");
    }

    @Override
    public void close() throws IOException {
        sortie.flush();
    }

    private String cellule(Object valeur) {
        if (valeur == null) {
            return "";
        }
        if (valeur instanceof BigDecimal nombre) {
            String texte = nombre.toPlainString();
            return decimal == SeparateurDecimal.VIRGULE ? texte.replace('.', ',') : texte;
        }
        if (valeur instanceof Number || valeur instanceof LocalDate) {
            return valeur.toString();
        }
        if (valeur instanceof LocalDateTime dateHeure) {
            return DATE_HEURE.format(dateHeure);
        }
        return proteger(valeur.toString());
    }

    private String proteger(String texte) {
        String sur = texte;
        if (!sur.isEmpty()) {
            for (char c : DEBUT_FORMULE) {
                if (sur.charAt(0) == c) {
                    sur = "'" + sur;
                    break;
                }
            }
        }
        boolean guillemets = sur.indexOf(separateur) >= 0 || sur.indexOf('"') >= 0
                || sur.indexOf('\n') >= 0 || sur.indexOf('\r') >= 0;
        return guillemets ? "\"" + sur.replace("\"", "\"\"") + "\"" : sur;
    }
}
