package com.batulo.service.export;

import com.batulo.service.model.CroisementModel;
import com.batulo.service.model.StatistiquesModel;
import com.batulo.service.model.StatistiquesModel.QuestionStat;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Statistiques en Excel (module 4.4) : une feuille « Résumé », puis une feuille par question avec son tableau ;
 * ou une feuille pour un tableau croisé. Les statistiques sont déjà agrégées (petit volume) : classeur XSSF classique.
 * Nombres et pourcentages en cellules numériques, pour pouvoir faire des graphiques dans Excel.
 */
public class RapportXlsx {

    private static final int LONGUEUR_NOM_FEUILLE = 31;

    private final InfosRapport infos;
    private XSSFWorkbook classeur;
    private CellStyle gras;
    private CellStyle pourcentage;
    private final Set<String> nomsPris = new HashSet<>();

    public RapportXlsx(InfosRapport infos) {
        this.infos = infos;
    }

    public void statistiques(StatistiquesModel stats, OutputStream flux) {
        nouveauClasseur();
        Sheet resume = feuille("Résumé");
        int ligne = entete(resume, "Rapport de statistiques");
        valeur(resume, ligne++, "Réponses", stats.resume().nombreReponses());
        if (stats.resume().petitEffectif()) {
            texte(resume, ligne++, "Attention", "Petit effectif : moins de " + infos.effectifMinimum() + " réponses.");
        }
        ligne++;
        titres(resume, ligne++, "N°", "Question", "Section", "Type", "Répondants", "Sans réponse", "Petit effectif");
        int numero = 0;
        for (QuestionStat q : stats.questions()) {
            numero++;
            Row row = resume.createRow(ligne++);
            nombre(row, 0, numero);
            row.createCell(1).setCellValue(q.libelle());
            row.createCell(2).setCellValue(q.section());
            row.createCell(3).setCellValue(RapportPdf.libelleType(q.type()));
            nombre(row, 4, q.nombreRepondants());
            nombre(row, 5, q.sansReponse());
            row.createCell(6).setCellValue(q.petitEffectif() ? "Oui" : "Non");
        }
        largeurs(resume, 0, 6, 40, 20, 16, 12, 12, 14);

        numero = 0;
        for (QuestionStat q : stats.questions()) {
            question(feuille("Q" + (++numero) + " " + q.libelle()), q);
        }
        ecrire(flux);
    }

    public void croisement(CroisementModel t, OutputStream flux) {
        nouveauClasseur();
        Sheet feuille = feuille("Tableau croisé");
        int ligne = entete(feuille, "Tableau croisé : « " + t.question().libelle() + " » par « " + t.par().libelle() + " »");
        ligne++;
        if (t.lignes() != null) {
            List<String> titres = new ArrayList<>(List.of(t.question().libelle()));
            for (CroisementModel.Colonne c : t.colonnes()) {
                titres.add(c.libelle() + " (nombre)");
                titres.add(c.libelle() + " (% colonne)");
            }
            titres.add("Total");
            titres(feuille, ligne++, titres.toArray(String[]::new));
            Row effectifs = feuille.createRow(ligne++);
            effectifs.createCell(0).setCellValue("Répondants");
            for (int i = 0; i < t.colonnes().size(); i++) {
                nombre(effectifs, 1 + 2 * i, t.colonnes().get(i).nombreRepondants());
                if (t.colonnes().get(i).petitEffectif()) {
                    effectifs.createCell(2 + 2 * i).setCellValue("petit effectif");
                }
            }
            for (CroisementModel.Ligne l : t.lignes()) {
                Row row = feuille.createRow(ligne++);
                row.createCell(0).setCellValue(l.libelle());
                for (int i = 0; i < l.cellules().size(); i++) {
                    nombre(row, 1 + 2 * i, l.cellules().get(i).nombre());
                    pct(row, 2 + 2 * i, l.cellules().get(i).pourcentageColonne());
                }
                nombre(row, 1 + 2 * l.cellules().size(), l.total());
            }
            ligne++;
        }
        if (t.numerique() != null) {
            titres(feuille, ligne++, t.par().libelle(), "Nombre", "Moyenne", "Médiane", "Minimum", "Maximum", "Petit effectif");
            for (CroisementModel.NumeriqueColonne n : t.numerique()) {
                Row row = feuille.createRow(ligne++);
                row.createCell(0).setCellValue(t.colonnes().stream()
                        .filter(c -> Objects.equals(c.valeur(), n.colonne()))
                        .map(CroisementModel.Colonne::libelle).findFirst().orElse(String.valueOf(n.colonne())));
                nombre(row, 1, n.nombre());
                decimal(row, 2, n.moyenne());
                decimal(row, 3, n.mediane());
                decimal(row, 4, n.min());
                decimal(row, 5, n.max());
                row.createCell(6).setCellValue(n.petitEffectif() ? "Oui" : "Non");
            }
        }
        feuille.setColumnWidth(0, 40 * 256);
        ecrire(flux);
    }

    // ---------------------------------------------------------------- Une feuille par question

    private void question(Sheet feuille, QuestionStat q) {
        int ligne = 0;
        texte(feuille, ligne++, "Question", q.libelle());
        texte(feuille, ligne++, "Section", q.section());
        texte(feuille, ligne++, "Type", RapportPdf.libelleType(q.type()));
        valeur(feuille, ligne++, "Répondants", q.nombreRepondants());
        valeur(feuille, ligne++, "Sans réponse", q.sansReponse());
        if (q.petitEffectif()) {
            texte(feuille, ligne++, "Attention", "Petit effectif : à interpréter avec prudence.");
        }
        ligne++;
        if (q.options() != null) {
            titres(feuille, ligne++, "Réponse", "Valeur", "Nombre", "%");
            for (StatistiquesModel.OptionStat o : q.options()) {
                Row row = feuille.createRow(ligne++);
                row.createCell(0).setCellValue(o.libelle());
                row.createCell(1).setCellValue(o.valeur());
                nombre(row, 2, o.nombre());
                pct(row, 3, o.pourcentage());
            }
        } else if (q.numerique() != null) {
            StatistiquesModel.Numerique n = q.numerique();
            titres(feuille, ligne++, "Moyenne", "Médiane", "Minimum", "Maximum");
            Row row = feuille.createRow(ligne++);
            decimal(row, 0, n.moyenne());
            decimal(row, 1, n.mediane());
            decimal(row, 2, n.min());
            decimal(row, 3, n.max());
            ligne++;
            titres(feuille, ligne++, "Valeur ou tranche (de)", "à", "Nombre", "%");
            for (StatistiquesModel.Classe c : n.distribution()) {
                Row r = feuille.createRow(ligne++);
                decimal(r, 0, c.valeur() != null ? c.valeur() : c.de());
                decimal(r, 1, c.a());
                nombre(r, 2, c.nombre());
                pct(r, 3, c.pourcentage());
            }
        } else if (q.dates() != null) {
            titres(feuille, ligne++, "Période", "Nombre");
            for (StatistiquesModel.Periode p : q.dates().repartition()) {
                Row row = feuille.createRow(ligne++);
                row.createCell(0).setCellValue(p.periode());
                nombre(row, 1, p.nombre());
            }
        } else if (q.classement() != null) {
            titres(feuille, ligne++, "Option", "Valeur", "Rang moyen", "Classée 1re");
            for (StatistiquesModel.RangStat r : q.classement()) {
                Row row = feuille.createRow(ligne++);
                row.createCell(0).setCellValue(r.libelle());
                row.createCell(1).setCellValue(r.valeur());
                decimal(row, 2, r.rangMoyen());
                nombre(row, 3, r.nombrePremier());
            }
        } else if (q.matrice() != null) {
            List<String> titres = new ArrayList<>(List.of("Ligne"));
            q.matrice().colonnes().forEach(c -> {
                titres.add(c + " (nombre)");
                titres.add(c + " (%)");
            });
            titres(feuille, ligne++, titres.toArray(String[]::new));
            for (StatistiquesModel.LigneMatrice l : q.matrice().lignes()) {
                Row row = feuille.createRow(ligne++);
                row.createCell(0).setCellValue(l.ligne());
                for (int i = 0; i < l.cellules().size(); i++) {
                    nombre(row, 1 + 2 * i, l.cellules().get(i).nombre());
                    pct(row, 2 + 2 * i, l.cellules().get(i).pourcentage());
                }
            }
        } else if (q.textes() != null) {
            titres(feuille, ligne++, "Texte", "Date");
            for (StatistiquesModel.Texte t : q.textes().contenu()) {
                Row row = feuille.createRow(ligne++);
                row.createCell(0).setCellValue(t.texte());
                row.createCell(1).setCellValue(t.dateSoumission().toString());
            }
            if (q.textes().totalElements() > q.textes().contenu().size()) {
                texte(feuille, ligne + 1, "Note", "Les " + q.textes().contenu().size() + " plus récents sur "
                        + q.textes().totalElements() + " : tous les textes sont dans l'export des réponses.");
            }
        }
        largeurs(feuille, 0, 40, 18, 14, 14);
    }

    // ---------------------------------------------------------------- Outils

    private void nouveauClasseur() {
        classeur = new XSSFWorkbook();
        Font fontGras = classeur.createFont();
        fontGras.setBold(true);
        gras = classeur.createCellStyle();
        gras.setFont(fontGras);
        pourcentage = classeur.createCellStyle();
        pourcentage.setDataFormat(classeur.createDataFormat().getFormat("0.0"));
    }

    /** Lignes d'information communes ; renvoie la ligne suivante. */
    private int entete(Sheet feuille, String nature) {
        int ligne = 0;
        texte(feuille, ligne++, nature, infos.titre());
        texte(feuille, ligne++, "Entreprise", infos.entreprise());
        texte(feuille, ligne++, "Périmètre", infos.perimetre());
        texte(feuille, ligne++, "Période", infos.periode());
        texte(feuille, ligne++, "Critères", infos.criteres().isEmpty() ? "Tous les répondants"
                : String.join(" ; ", infos.criteres()));
        texte(feuille, ligne++, "Généré le", infos.genereLe());
        return ligne;
    }

    /** Nom de feuille Excel : 31 caractères au plus, sans []:*?/\, unique dans le classeur. */
    private Sheet feuille(String nom) {
        String propre = WorkbookUtil.createSafeSheetName(nom);
        propre = propre.length() > LONGUEUR_NOM_FEUILLE ? propre.substring(0, LONGUEUR_NOM_FEUILLE) : propre;
        String unique = propre;
        for (int i = 2; !nomsPris.add(unique.toLowerCase()); i++) {
            String suffixe = " (" + i + ")";
            unique = propre.substring(0, Math.min(propre.length(), LONGUEUR_NOM_FEUILLE - suffixe.length())) + suffixe;
        }
        return classeur.createSheet(unique);
    }

    private void titres(Sheet feuille, int ligne, String... titres) {
        Row row = feuille.createRow(ligne);
        for (int i = 0; i < titres.length; i++) {
            Cell cellule = row.createCell(i);
            cellule.setCellValue(titres[i]);
            cellule.setCellStyle(gras);
        }
    }

    private void texte(Sheet feuille, int ligne, String libelle, String valeur) {
        Row row = feuille.createRow(ligne);
        Cell titre = row.createCell(0);
        titre.setCellValue(libelle);
        titre.setCellStyle(gras);
        row.createCell(1).setCellValue(valeur == null ? "" : valeur);
    }

    private void valeur(Sheet feuille, int ligne, String libelle, long valeur) {
        Row row = feuille.createRow(ligne);
        Cell titre = row.createCell(0);
        titre.setCellValue(libelle);
        titre.setCellStyle(gras);
        nombre(row, 1, valeur);
    }

    private static void nombre(Row row, int colonne, long valeur) {
        row.createCell(colonne).setCellValue(valeur);
    }

    private static void decimal(Row row, int colonne, BigDecimal valeur) {
        if (valeur != null) {
            row.createCell(colonne).setCellValue(valeur.doubleValue());
        }
    }

    private void pct(Row row, int colonne, BigDecimal valeur) {
        if (valeur != null) {
            Cell cellule = row.createCell(colonne);
            cellule.setCellValue(valeur.doubleValue());
            cellule.setCellStyle(pourcentage);
        }
    }

    private static void largeurs(Sheet feuille, int premiere, int... largeurs) {
        for (int i = 0; i < largeurs.length; i++) {
            feuille.setColumnWidth(premiere + i, largeurs[i] * 256);
        }
    }

    private void ecrire(OutputStream flux) {
        try (XSSFWorkbook aFermer = classeur) {
            aFermer.write(flux);
            flux.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
