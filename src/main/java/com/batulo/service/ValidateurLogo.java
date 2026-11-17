package com.batulo.service;

import com.batulo.exception.FichierTropGrosException;
import com.batulo.exception.RegleGestionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

/**
 * Contrôle d'un logo envoyé : seuls PNG et JPEG sont acceptés (ni SVG, qui peut contenir du script, ni GIF, ni
 * WebP pour l'instant). Le type est déterminé par les premiers octets du fichier, jamais par son nom ni par le
 * Content-Type envoyé. Les dimensions sont lues dans l'en-tête avant tout décodage (pas de « bombe de
 * décompression »), puis l'image est décodée une fois pour refuser un fichier corrompu.
 */
@Component
public class ValidateurLogo {

    /** Type réel, taille et dimensions d'un logo accepté. */
    public record LogoValide(String typeMime, int largeur, int hauteur) {
    }

    private static final byte[] SIGNATURE_PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] SIGNATURE_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final long tailleMax;
    private final int dimensionMax;

    public ValidateurLogo(@Value("${batulo.personnalisation.logo-taille-max:1048576}") long tailleMax,
                          @Value("${batulo.personnalisation.logo-dimension-max:2000}") int dimensionMax) {
        this.tailleMax = tailleMax;
        this.dimensionMax = dimensionMax;
    }

    public LogoValide valider(byte[] contenu) {
        if (contenu == null || contenu.length == 0) {
            throw new RegleGestionException("LOGO_VIDE", "Le fichier du logo est vide.");
        }
        if (contenu.length > tailleMax) {
            throw new FichierTropGrosException("Le logo ne doit pas dépasser " + (tailleMax / 1024) + " Ko.");
        }
        String type = typeReel(contenu);
        if (type == null) {
            throw new RegleGestionException("LOGO_FORMAT_INVALIDE",
                    "Le logo doit être une image PNG ou JPEG (le contenu du fichier ne correspond à aucun des deux).");
        }
        try (ImageInputStream flux = ImageIO.createImageInputStream(new ByteArrayInputStream(contenu))) {
            Iterator<ImageReader> lecteurs = ImageIO.getImageReadersByMIMEType(type);
            if (!lecteurs.hasNext()) {
                throw illisible();
            }
            ImageReader lecteur = lecteurs.next();
            try {
                lecteur.setInput(flux, true, true);
                int largeur = lecteur.getWidth(0);
                int hauteur = lecteur.getHeight(0);
                if (largeur < 1 || hauteur < 1 || largeur > dimensionMax || hauteur > dimensionMax) {
                    throw new RegleGestionException("LOGO_DIMENSIONS", "Le logo doit mesurer au plus " + dimensionMax
                            + " x " + dimensionMax + " pixels (reçu : " + largeur + " x " + hauteur + ").");
                }
                BufferedImage image = lecteur.read(0);
                if (image == null) {
                    throw illisible();
                }
                return new LogoValide(type, largeur, hauteur);
            } finally {
                lecteur.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof RegleGestionException regle) {
                throw regle;
            }
            throw illisible();
        }
    }

    /** image/png, image/jpeg, ou null pour tout autre contenu. */
    static String typeReel(byte[] contenu) {
        if (commencePar(contenu, SIGNATURE_PNG)) {
            return "image/png";
        }
        if (commencePar(contenu, SIGNATURE_JPEG)) {
            return "image/jpeg";
        }
        return null;
    }

    private static boolean commencePar(byte[] contenu, byte[] signature) {
        return contenu.length >= signature.length
                && Arrays.equals(Arrays.copyOf(contenu, signature.length), signature);
    }

    private static RegleGestionException illisible() {
        return new RegleGestionException("LOGO_ILLISIBLE",
                "Le logo est illisible ou endommagé : enregistrez-le de nouveau en PNG ou en JPEG (couleurs RVB).");
    }
}
