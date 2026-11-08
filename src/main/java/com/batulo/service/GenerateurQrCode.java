package com.batulo.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** QR code (image PNG) d'un lien de sondage (RE-01), avec la bibliothèque ZXing. */
@Component
public class GenerateurQrCode {

    public byte[] png(String texte, int taille) {
        Map<EncodeHintType, Object> options = Map.of(
                EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name(),
                // Niveau M : reste lisible si l'impression est un peu abîmée.
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN, 1);
        try {
            BitMatrix matrice = new QRCodeWriter().encode(texte, BarcodeFormat.QR_CODE, taille, taille, options);
            ByteArrayOutputStream sortie = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrice, "PNG", sortie);
            return sortie.toByteArray();
        } catch (WriterException e) {
            throw new IllegalStateException("Impossible de générer le QR code", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
