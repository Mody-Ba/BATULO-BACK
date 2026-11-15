package com.batulo.service.paiement;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Signature des webhooks Moosyl : en-tête x-webhook-signature = « sha256= » suivi du HMAC-SHA256 hexadécimal du
 * corps brut, avec le secret du webhook (https://docs.moosyl.com/webhooks).
 */
public final class SignatureWebhookMoosyl {

    public static final String EN_TETE = "x-webhook-signature";
    private static final String PREFIXE = "sha256=";

    private SignatureWebhookMoosyl() {
    }

    /** Valeur de l'en-tête pour ce corps (utilisée par la route de développement qui simule Moosyl). */
    public static String signer(byte[] corps, String secret) {
        return PREFIXE + HexFormat.of().formatHex(hmac(corps, secret));
    }

    /** Comparaison en temps constant ; false si le secret est vide ou l'en-tête absent ou mal formé. */
    public static boolean verifier(byte[] corps, String enTete, String secret) {
        if (secret == null || secret.isEmpty() || enTete == null) {
            return false;
        }
        String recu = enTete.trim();
        if (recu.toLowerCase(Locale.ROOT).startsWith(PREFIXE)) {
            recu = recu.substring(PREFIXE.length());
        }
        if (!recu.matches("[0-9a-fA-F]{64}")) {
            return false;
        }
        return MessageDigest.isEqual(HexFormat.of().parseHex(recu.toLowerCase(Locale.ROOT)), hmac(corps, secret));
    }

    private static byte[] hmac(byte[] corps, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(corps);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }
}
