package com.batulo.service.export;

import com.batulo.enums.TypeQuestion;
import com.batulo.service.model.CroisementModel;
import com.batulo.service.model.StatistiquesModel.QuestionStat;
import com.batulo.service.model.StatistiquesModel;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.FontSelector;
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
 * l'impression). Polices libres embarquées : Liberation Sans, et Noto Sans Arabic (licence OFL, fichier joint) pour
 * l'arabe, choisies caractère par caractère. Un texte qui contient de l'arabe est écrit de droite à gauche dans une
 * cellule (OpenPDF lie alors les lettres selon leur position) ; les autres textes restent de gauche à droite.
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
    private static final BaseFont ARABE = police("polices/noto/NotoSansArabic-Regular.ttf");
    private static final BaseFont ARABE_GRAS = police("polices/noto/NotoSansArabic-Bold.ttf");

    private final Font titre = new Font(GRAS, 22, Font.NORMAL, BLEU);
    private final Font sousTitre = new Font(GRAS, 13, Font.NORMAL, BLEU);
    private final Font texte = new Font(NORMAL, 9.5f);
    private final Font texteGras = new Font(GRAS, 9.5f);
    private final Font discret = new Font(NORMAL, 8.5f, Font.NORMAL, GRIS_TEXTE);
    private final Font alerte = new Font(NORMAL, 8.5f, Font.NORMAL, ORANGE);

    /** Hauteur du logo dans l'en-tête des pages, et sa largeur maximale. */
    private static final float LOGO_ENTETE_HAUTEUR = 16;
    private static final float LOGO_ENTETE_LARGEUR_MAX = 80;

    private final InfosRapport infos;
    /** Logo de l'entreprise (PNG ou JPEG, contrôlé à l'envoi) ; null s'il n'y en a pas ou s'il est illisible. */
    private final Image logo;
    private PdfWriter ecrivain;

    public RapportPdf(InfosRapport infos) {
        this.infos = infos;
        this.logo = chargerLogo(infos.logo());
    }

    /** Un logo illisible ne doit jamais empêcher le rapport : il est alors simplement omis. */
    private static Image chargerLogo(byte[] octets) {
        if (octets == null) {
            return null;
        }
        try {
            return Image.getInstance(octets);
        } catch (IOException | RuntimeException e) {
            return null;
        }
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
            document.add(bloc(infos.titre(), titre, 0, 0, 0));
            document.add(bloc("Tableau croisé : « " + tableau.question().libelle() + " » par « "
                    + tableau.par().libelle() + " »", sousTitre, 0, 0, 0));
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
        espace.setSpacingBefore(logo == null && !infos.personnalise() ? 120 : 40);
        document.add(espace);
        if (logo != null) {
            Image grand = Image.getInstance(logo);
            grand.scaleToFit(180, 80);
            grand.setAlignment(Element.ALIGN_LEFT);
            document.add(grand);
        }
        if (infos.personnalise()) {
            document.add(bloc(infos.entreprise(), texteGras, 6, 40, 0));
        }
        document.add(new Paragraph(nature, sousTitre));
        document.add(bloc(infos.titre(), titre, 0, 24, 0));
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
        PdfPCell gauche = sens(new PdfPCell(phrase(libelle, texteGras)), libelle);
        gauche.setBorder(Rectangle.NO_BORDER);
        gauche.setPaddingBottom(5);
        PdfPCell droite = sens(new PdfPCell(phrase(valeur, texte)), valeur);
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
        document.add(bloc(numero + ". " + q.libelle(), sousTitre, 14, 0, 0));
        String details = libelleType(q.type()) + " · " + q.nombreRepondants()
                + " répondant(s) · " + q.sansReponse() + " sans réponse"
                + (q.conditionnelle() ? " · question conditionnelle, vue par " + q.vues() : "");
        if (contientArabe(q.section())) {
            // Section en arabe sur sa propre ligne (de droite à gauche), les détails en français dessous.
            document.add(bloc(q.section(), discret, 0, 0, 0));
            document.add(new Paragraph(details, discret));
        } else {
            document.add(new Paragraph(q.section() + " · " + details, discret));
        }
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
                document.add(bloc("« " + t.texte() + " »", texte, 2, 0, 12));
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
        entetes.addAll(m.libellesColonnes());
        entetes.add("Réponses");
        PdfPTable table = table(largeurs, entetes.toArray(String[]::new));
        for (StatistiquesModel.LigneMatrice ligne : m.lignes()) {
            table.addCell(cellule(ligne.libelle(), Element.ALIGN_LEFT));
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
            PdfPCell cellule = sens(new PdfPCell(phrase(entete, texteGras)), entete);
            cellule.setBackgroundColor(GRIS_ENTETE);
            cellule.setPadding(4);
            cellule.setBorderColor(GRIS_ENTETE);
            table.addCell(cellule);
        }
        return table;
    }

    private PdfPCell cellule(String valeur, int alignement) {
        PdfPCell cellule = sens(new PdfPCell(phrase(valeur, texte)), valeur);
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
            float xTexte = document.left();
            if (logo != null) {
                float largeur = Math.min(LOGO_ENTETE_LARGEUR_MAX,
                        LOGO_ENTETE_HAUTEUR * logo.getWidth() / Math.max(1, logo.getHeight()));
                float hauteur = largeur * logo.getHeight() / Math.max(1, logo.getWidth());
                try {
                    cb.addImage(logo, largeur, 0, 0, hauteur, document.left(), page.getTop() - 34);
                } catch (DocumentException e) {
                    throw new IllegalStateException("Logo impossible à placer dans l'en-tête", e);
                }
                xTexte += largeur + 6;
            }
            String auteur = infos.personnalise() ? infos.entreprise() : "Batulo";
            // ColumnText : sélection des polices et écriture de droite à gauche si l'en-tête contient de l'arabe.
            String entete = auteur + " · " + infos.titre();
            boolean arabe = contientArabe(entete);
            ColumnText.showTextAligned(cb, arabe ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT,
                    phrase(entete, new Font(NORMAL, 8, Font.NORMAL, GRIS_TEXTE)),
                    arabe ? document.right() : xTexte, page.getTop() - 30, 0,
                    arabe ? PdfWriter.RUN_DIRECTION_RTL : PdfWriter.RUN_DIRECTION_LTR, 0);
            cb.beginText();
            cb.setFontAndSize(NORMAL, 8);
            cb.setColorFill(GRIS_TEXTE);
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

    // ---------------------------------------------------------------- Polices et sens d'écriture

    /** Texte dans la police demandée, et Noto Sans Arabic pour les caractères arabes (même taille, style, couleur). */
    private static Phrase phrase(String valeur, Font police) {
        String texte = valeur == null ? "" : valeur;
        if (!contientArabe(texte)) {
            return new Phrase(texte, police);
        }
        FontSelector selecteur = new FontSelector();
        selecteur.addFont(police);
        selecteur.addFont(new Font(police.getBaseFont() == GRAS ? ARABE_GRAS : ARABE, police.getSize(),
                police.getStyle(), police.getColor()));
        return selecteur.process(texte);
    }

    /** Une cellule qui contient de l'arabe s'écrit de droite à gauche (lettres liées, ordre bidirectionnel). */
    private static PdfPCell sens(PdfPCell cellule, String valeur) {
        if (contientArabe(valeur)) {
            cellule.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
        }
        return cellule;
    }

    /**
     * Paragraphe de texte libre (titres, libellés, réponses). Avec de l'arabe : une cellule sans bordure écrite de
     * droite à gauche, car OpenPDF ne façonne l'arabe que dans les cellules et les colonnes, pas dans un paragraphe.
     */
    private Element bloc(String valeur, Font police, float avant, float apres, float retrait) {
        if (!contientArabe(valeur)) {
            Paragraph paragraphe = new Paragraph(phrase(valeur, police));
            paragraphe.setSpacingBefore(avant);
            paragraphe.setSpacingAfter(apres);
            paragraphe.setIndentationLeft(retrait);
            paragraphe.setKeepTogether(true);
            return paragraphe;
        }
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(avant);
        table.setSpacingAfter(apres);
        PdfPCell cellule = sens(new PdfPCell(phrase(valeur, police)), valeur);
        cellule.setBorder(Rectangle.NO_BORDER);
        cellule.setPadding(0);
        cellule.setPaddingBottom(3);
        cellule.setPaddingRight(retrait);
        table.addCell(cellule);
        return table;
    }

    static boolean contientArabe(String texte) {
        if (texte == null) {
            return false;
        }
        return texte.codePoints().anyMatch(c -> (c >= 0x0600 && c <= 0x06FF) || (c >= 0x0750 && c <= 0x077F)
                || (c >= 0x08A0 && c <= 0x08FF) || (c >= 0xFB50 && c <= 0xFDFF) || (c >= 0xFE70 && c <= 0xFEFF));
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
