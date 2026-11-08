package com.batulo.service.export;

import com.batulo.enums.TypeQuestion;
import com.batulo.service.model.CroisementModel;
import com.batulo.service.model.StatistiquesModel;
import com.batulo.service.model.StatistiquesModel.QuestionStat;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Rapport PDF (module 4.4) construit à partir des statistiques déjà calculées : page de titre, puis une section par
 * question avec son tableau et un graphique en barres dessiné directement dans le tableau (vectoriel, net à
 * l'impression). Police libre embarquée (Liberation Sans) : tous les caractères des réponses s'affichent.
 */
public class RapportPdf {

    private static final Color BLEU = new Color(0x2F, 0x5D, 0x9E);
    private static final Color GRIS_ENTETE = new Color(0xE8, 0xEC, 0xF2);
    private static final Color GRIS_TEXTE = new Color(0x55, 0x5F, 0x6D);
    private static final Color ORANGE = new Color(0xB4, 0x5F, 0x06);
    private static final int TEXTES_MAX = 20;
    /** En dessous de cette hauteur restante, une nouvelle question commence sur la page suivante (pas de titre orphelin). */
    private static final float HAUTEUR_MIN_QUESTION = 160;

    private static final BaseFont NORMAL = police("liberation/LiberationSans-Regular.ttf");
    private static final BaseFont GRAS = police("liberation/LiberationSans-Bold.ttf");

    private final Font titre = new Font(GRAS, 22, Font.NORMAL, BLEU);
    private final Font sousTitre = new Font(GRAS, 13, Font.NORMAL, BLEU);
    private final Font texte = new Font(NORMAL, 9.5f);
    private final Font texteGras = new Font(GRAS, 9.5f);
    private final Font discret = new Font(NORMAL, 8.5f, Font.NORMAL, GRIS_TEXTE);
    private final Font alerte = new Font(NORMAL, 8.5f, Font.NORMAL, ORANGE);

    private final InfosRapport infos;
    private PdfWriter ecrivain;

    public RapportPdf(InfosRapport infos) {
        this.infos = infos;
    }

    public void statistiques(StatistiquesModel stats, OutputStream flux) {
        Document document = ouvrir(PageSize.A4, flux);
        try {
            pageDeTitre(document, "Rapport de statistiques", stats.resume().nombreReponses(),
                    stats.resume().petitEffectif());
            document.newPage();
            int numero = 0;
            for (QuestionStat question : stats.questions()) {
                question(document, ++numero, question);
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Génération du rapport PDF impossible", e);
        } finally {
            document.close();
        }
    }

    public void croisement(CroisementModel tableau, OutputStream flux) {
        Document document = ouvrir(PageSize.A4.rotate(), flux);
        try {
            document.add(new Paragraph(infos.titre(), titre));
            document.add(new Paragraph("Tableau croisé : « " + tableau.question().libelle() + " » par « "
                    + tableau.par().libelle() + " »", sousTitre));
            document.add(new Paragraph(" "));
            document.add(tableauInfos(null, false));
            document.add(new Paragraph(" "));
            if (tableau.lignes() != null) {
                document.add(croisementLignes(tableau));
                document.add(new Paragraph("Chaque cellule : nombre de répondants et % de la colonne. "
                        + "* moins de " + infos.effectifMinimum() + " répondants : à interpréter avec prudence.", discret));
            }
            if (tableau.numerique() != null) {
                document.add(new Paragraph(" "));
                document.add(croisementNumerique(tableau));
            }
        } catch (DocumentException e) {
            throw new IllegalStateException("Génération du rapport PDF impossible", e);
        } finally {
            document.close();
        }
    }

    // ---------------------------------------------------------------- Pages

    private Document ouvrir(Rectangle format, OutputStream flux) {
        Document document = new Document(format, 40, 40, 55, 45);
        ecrivain = PdfWriter.getInstance(document, flux);
        // Le flux de la réponse HTTP appartient à l'appelant : le PDF ne le ferme pas.
        ecrivain.setCloseStream(false);
        ecrivain.setPageEvent(new EnteteEtPied());
        document.addTitle(infos.titre());
        document.addCreator("Batulo");
        document.open();
        return document;
    }

    private void pageDeTitre(Document document, String nature, long reponses, boolean petitEffectif)
            throws DocumentException {
        Paragraph espace = new Paragraph(" ");
        espace.setSpacingBefore(120);
        document.add(espace);
        document.add(new Paragraph(nature, sousTitre));
        Paragraph nom = new Paragraph(infos.titre(), titre);
        nom.setSpacingAfter(24);
        document.add(nom);
        document.add(tableauInfos(reponses, petitEffectif));
    }

    private PdfPTable tableauInfos(Long reponses, boolean petitEffectif) {
        PdfPTable table = new PdfPTable(new float[]{1.3f, 4f});
        table.setWidthPercentage(80);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        ligneInfo(table, "Entreprise", infos.entreprise());
        ligneInfo(table, "Périmètre", infos.perimetre());
        ligneInfo(table, "Période", infos.periode());
        ligneInfo(table, "Critères", infos.criteres().isEmpty() ? "Tous les répondants"
                : String.join("\n", infos.criteres()));
        if (reponses != null) {
            ligneInfo(table, "Réponses", reponses + (petitEffectif
                    ? "  (petit effectif : moins de " + infos.effectifMinimum() + ", à interpréter avec prudence)" : ""));
        }
        ligneInfo(table, "Généré le", infos.genereLe());
        return table;
    }

    private void ligneInfo(PdfPTable table, String libelle, String valeur) {
        PdfPCell gauche = new PdfPCell(new Phrase(libelle, texteGras));
        gauche.setBorder(Rectangle.NO_BORDER);
        gauche.setPaddingBottom(5);
        PdfPCell droite = new PdfPCell(new Phrase(valeur, texte));
        droite.setBorder(Rectangle.NO_BORDER);
        droite.setPaddingBottom(5);
        table.addCell(gauche);
        table.addCell(droite);
    }

    // ---------------------------------------------------------------- Questions

    private void question(Document document, int numero, QuestionStat q) throws DocumentException {
        if (ecrivain.getVerticalPosition(true) - document.bottom() < HAUTEUR_MIN_QUESTION) {
            document.newPage();
        }
        Paragraph entete = new Paragraph(numero + ". " + q.libelle(), sousTitre);
        entete.setSpacingBefore(14);
        entete.setKeepTogether(true);
        document.add(entete);
        document.add(new Paragraph(q.section() + " · " + libelleType(q.type()) + " · " + q.nombreRepondants()
                + " répondant(s) · " + q.sansReponse() + " sans réponse", discret));
        if (q.petitEffectif()) {
            document.add(new Paragraph("Petit effectif : moins de " + infos.effectifMinimum()
                    + " répondants, à interpréter avec prudence.", alerte));
        }
        document.add(new Paragraph(" ", new Font(NORMAL, 4)));

        if (q.options() != null) {
            PdfPTable table = tableAvecBarres("Réponse");
            for (StatistiquesModel.OptionStat o : q.options()) {
                ligneBarre(table, o.libelle(), String.valueOf(o.nombre()), pct(o.pourcentage()), fraction(o.pourcentage()));
            }
            document.add(table);
        } else if (q.numerique() != null) {
            StatistiquesModel.Numerique n = q.numerique();
            PdfPTable resume = table(new float[]{1, 1, 1, 1}, "Moyenne", "Médiane", "Minimum", "Maximum");
            for (BigDecimal valeur : new BigDecimal[]{n.moyenne(), n.mediane(), n.min(), n.max()}) {
                resume.addCell(cellule(nombre(valeur), Element.ALIGN_CENTER));
            }
            document.add(resume);
            if (!n.distribution().isEmpty()) {
                document.add(new Paragraph(" ", new Font(NORMAL, 4)));
                PdfPTable table = tableAvecBarres("Valeur");
                for (StatistiquesModel.Classe c : n.distribution()) {
                    String libelle = c.valeur() != null ? nombre(c.valeur()) : nombre(c.de()) + " à " + nombre(c.a());
                    ligneBarre(table, libelle, String.valueOf(c.nombre()), pct(c.pourcentage()), fraction(c.pourcentage()));
                }
                document.add(table);
            }
        } else if (q.dates() != null) {
            StatistiquesModel.Dates d = q.dates();
            Paragraph etendue = new Paragraph(d.min() == null ? "Aucune date." : "Du " + d.min() + " au " + d.max()
                    + " (par " + (d.granularite().name().equals("JOUR") ? "jour" : "mois") + ")", texte);
            etendue.setSpacingAfter(6);
            document.add(etendue);
            long max = d.repartition().stream().mapToLong(StatistiquesModel.Periode::nombre).max().orElse(0);
            if (max > 0) {
                PdfPTable table = tableAvecBarres("Période");
                for (StatistiquesModel.Periode p : d.repartition()) {
                    BigDecimal part = BigDecimal.valueOf(p.nombre() * 100.0 / q.nombreRepondants())
                            .setScale(1, RoundingMode.HALF_UP);
                    ligneBarre(table, p.periode(), String.valueOf(p.nombre()), pct(part), (double) p.nombre() / max);
                }
                document.add(table);
            }
        } else if (q.classement() != null) {
            PdfPTable table = table(new float[]{3f, 1.2f, 1.2f, 3f}, "Option", "Rang moyen", "1re place", "Part classée 1re");
            for (StatistiquesModel.RangStat r : q.classement()) {
                table.addCell(cellule(r.libelle(), Element.ALIGN_LEFT));
                table.addCell(cellule(nombre(r.rangMoyen()), Element.ALIGN_RIGHT));
                table.addCell(cellule(String.valueOf(r.nombrePremier()), Element.ALIGN_RIGHT));
                table.addCell(barre(q.nombreRepondants() == 0 ? 0 : (double) r.nombrePremier() / q.nombreRepondants()));
            }
            document.add(table);
        } else if (q.matrice() != null) {
            document.add(matrice(q.matrice()));
        } else if (q.textes() != null) {
            document.add(new Paragraph(q.textes().totalElements() + " réponse(s) libre(s)"
                    + (q.textes().totalElements() > TEXTES_MAX ? ", les " + TEXTES_MAX + " plus récentes :" : " :"), texte));
            for (StatistiquesModel.Texte t : q.textes().contenu().stream().limit(TEXTES_MAX).toList()) {
                Paragraph ligne = new Paragraph("« " + t.texte() + " »", texte);
                ligne.setIndentationLeft(12);
                ligne.setSpacingBefore(2);
                document.add(ligne);
            }
        }
    }

    private PdfPTable matrice(StatistiquesModel.Matrice m) {
        float[] largeurs = new float[m.colonnes().size() + 2];
        largeurs[0] = 3f;
        for (int i = 1; i < largeurs.length; i++) {
            largeurs[i] = 1.4f;
        }
        List<String> entetes = new ArrayList<>(List.of("Ligne"));
        entetes.addAll(m.colonnes());
        entetes.add("Réponses");
        PdfPTable table = table(largeurs, entetes.toArray(String[]::new));
        for (StatistiquesModel.LigneMatrice ligne : m.lignes()) {
            table.addCell(cellule(ligne.ligne(), Element.ALIGN_LEFT));
            for (StatistiquesModel.Cellule c : ligne.cellules()) {
                table.addCell(cellule(c.nombre() + " (" + pct(c.pourcentage()) + ")", Element.ALIGN_CENTER));
            }
            table.addCell(cellule(String.valueOf(ligne.nombre()), Element.ALIGN_RIGHT));
        }
        return table;
    }

    // ---------------------------------------------------------------- Tableau croisé

    private PdfPTable croisementLignes(CroisementModel t) {
        float[] largeurs = new float[t.colonnes().size() + 2];
        largeurs[0] = 3f;
        for (int i = 1; i < largeurs.length; i++) {
            largeurs[i] = 1.5f;
        }
        List<String> entetes = new ArrayList<>(List.of(t.question().libelle()));
        t.colonnes().forEach(c -> entetes.add(c.libelle() + "\n(n = " + c.nombreRepondants() + ")"
                + (c.petitEffectif() ? " *" : "")));
        entetes.add("Total");
        PdfPTable table = table(largeurs, entetes.toArray(String[]::new));
        for (CroisementModel.Ligne ligne : t.lignes()) {
            table.addCell(cellule(ligne.libelle(), Element.ALIGN_LEFT));
            for (CroisementModel.Cellule c : ligne.cellules()) {
                table.addCell(cellule(c.nombre() + "\n" + pct(c.pourcentageColonne()), Element.ALIGN_CENTER));
            }
            table.addCell(cellule(String.valueOf(ligne.total()), Element.ALIGN_RIGHT));
        }
        return table;
    }

    private PdfPTable croisementNumerique(CroisementModel t) {
        PdfPTable table = table(new float[]{3f, 1, 1, 1, 1, 1}, t.par().libelle(), "Nombre", "Moyenne", "Médiane",
                "Minimum", "Maximum");
        for (CroisementModel.NumeriqueColonne n : t.numerique()) {
            String colonne = t.colonnes().stream().filter(c -> Objects.equals(c.valeur(), n.colonne()))
                    .map(CroisementModel.Colonne::libelle).findFirst().orElse(String.valueOf(n.colonne()));
            table.addCell(cellule(colonne + (n.petitEffectif() ? " *" : ""), Element.ALIGN_LEFT));
            table.addCell(cellule(String.valueOf(n.nombre()), Element.ALIGN_RIGHT));
            for (BigDecimal valeur : new BigDecimal[]{n.moyenne(), n.mediane(), n.min(), n.max()}) {
                table.addCell(cellule(nombre(valeur), Element.ALIGN_RIGHT));
            }
        }
        return table;
    }

    // ---------------------------------------------------------------- Tables et barres

    private PdfPTable tableAvecBarres(String premiereColonne) {
        return table(new float[]{3.2f, 1f, 1f, 3.2f}, premiereColonne, "Nombre", "%", "");
    }

    private void ligneBarre(PdfPTable table, String libelle, String nombre, String pourcentage, double fraction) {
        table.addCell(cellule(libelle, Element.ALIGN_LEFT));
        table.addCell(cellule(nombre, Element.ALIGN_RIGHT));
        table.addCell(cellule(pourcentage, Element.ALIGN_RIGHT));
        table.addCell(barre(fraction));
    }

    private PdfPTable table(float[] largeurs, String... entetes) {
        PdfPTable table = new PdfPTable(largeurs);
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String entete : entetes) {
            PdfPCell cellule = new PdfPCell(new Phrase(entete, texteGras));
            cellule.setBackgroundColor(GRIS_ENTETE);
            cellule.setPadding(4);
            cellule.setBorderColor(GRIS_ENTETE);
            table.addCell(cellule);
        }
        return table;
    }

    private PdfPCell cellule(String valeur, int alignement) {
        PdfPCell cellule = new PdfPCell(new Phrase(valeur, texte));
        cellule.setHorizontalAlignment(alignement);
        cellule.setPadding(4);
        cellule.setBorderColor(GRIS_ENTETE);
        return cellule;
    }

    /** Cellule vide où une barre proportionnelle est dessinée (0 à 1). */
    private PdfPCell barre(double fraction) {
        PdfPCell cellule = new PdfPCell(new Phrase(" ", texte));
        cellule.setBorderColor(GRIS_ENTETE);
        cellule.setCellEvent(new Barre(Math.max(0, Math.min(1, fraction))));
        return cellule;
    }

    private record Barre(double fraction) implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            if (fraction <= 0) {
                return;
            }
            PdfContentByte fond = canvases[PdfPTable.BACKGROUNDCANVAS];
            float largeur = (position.getWidth() - 6) * (float) fraction;
            fond.saveState();
            fond.setColorFill(BLEU);
            fond.rectangle(position.getLeft() + 3, position.getBottom() + 3, Math.max(1, largeur),
                    position.getHeight() - 6);
            fond.fill();
            fond.restoreState();
        }
    }

    /** En-tête (titre du sondage) et pied « page X / Y » sur chaque page. */
    private final class EnteteEtPied extends PdfPageEventHelper {

        private PdfTemplate total;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            total = writer.getDirectContent().createTemplate(40, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Rectangle page = document.getPageSize();
            cb.beginText();
            cb.setFontAndSize(NORMAL, 8);
            cb.setColorFill(GRIS_TEXTE);
            cb.showTextAligned(Element.ALIGN_LEFT, "Batulo · " + infos.titre(), document.left(), page.getTop() - 30, 0);
            String pageX = "Page " + writer.getPageNumber() + " / ";
            float x = document.right() - 40;
            cb.showTextAligned(Element.ALIGN_RIGHT, pageX, x, 25, 0);
            cb.endText();
            cb.addTemplate(total, x, 25);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            total.beginText();
            total.setFontAndSize(NORMAL, 8);
            total.setColorFill(GRIS_TEXTE);
            total.showText(String.valueOf(writer.getPageNumber() - 1));
            total.endText();
        }
    }

    // ---------------------------------------------------------------- Outils

    private static String pct(BigDecimal pourcentage) {
        return pourcentage == null ? "" : pourcentage.toPlainString().replace('.', ',') + " %";
    }

    /** Nombre à la française (virgule décimale) ; « – » s'il n'y en a pas. */
    private static String nombre(BigDecimal valeur) {
        return valeur == null ? "–" : valeur.toPlainString().replace('.', ',');
    }

    private static double fraction(BigDecimal pourcentage) {
        return pourcentage == null ? 0 : pourcentage.doubleValue() / 100;
    }

    static String libelleType(TypeQuestion type) {
        return switch (type) {
            case CHOIX_UNIQUE -> "Choix unique";
            case CHOIX_MULTIPLE -> "Choix multiple";
            case OUI_NON -> "Oui / Non";
            case TEXTE_COURT -> "Texte court";
            case TEXTE_LONG -> "Texte long";
            case ECHELLE -> "Échelle";
            case CLASSEMENT -> "Classement";
            case NUMERIQUE -> "Numérique";
            case DATE -> "Date";
            case MATRICE -> "Matrice";
        };
    }

    private static BaseFont police(String chemin) {
        try (InputStream entree = RapportPdf.class.getClassLoader().getResourceAsStream(chemin)) {
            if (entree == null) {
                throw new IllegalStateException("Police introuvable : " + chemin);
            }
            byte[] octets = entree.readAllBytes();
            return BaseFont.createFont(chemin.substring(chemin.lastIndexOf('/') + 1), BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED, BaseFont.CACHED, octets, null);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
