package com.batulo.service;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Jetons des liens personnels d'invitation : jeton = base64url(HMAC-SHA256(INVITATION_SECRET, sel)), 43 caractères
 * (256 bits). Seuls le sel et le SHA-256 du jeton sont en base : une copie de la base ne donne aucun lien utilisable
 * sans le secret, et le même lien est recalculé pour chaque relance. Changer le sel (ou le secret) invalide le lien.
 */
@Component
public class JetonsInvitation {

    private static final int TAILLE_MIN_SECRET_OCTETS = 32;
    private static final String ALGORITHME = "HmacSHA256";

    private final String secret;
    private SecretKeySpec cle;

    public JetonsInvitation(@Value("${batulo.invitations.secret:}") String secret) {
        this.secret = secret;
    }

    @PostConstruct
    void init() {
        byte[] octets = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (octets.length < TAILLE_MIN_SECRET_OCTETS) {
            throw new IllegalStateException("INVITATION_SECRET est absent ou trop court : il faut au moins "
                    + TAILLE_MIN_SECRET_OCTETS + " octets, dédiés aux liens d'invitation (jamais JWT_SECRET). "
                    + "Ajoutez-le dans .env.properties (voir .env.properties.example).");
        }
        this.cle = new SecretKeySpec(octets, ALGORITHME);
    }

    /** Nouveau sel aléatoire (256 bits, 43 caractères). */
    public String nouveauSel() {
        return HachageJeton.nouveauJeton();
    }

    /** Jeton du lien, recalculé à partir du sel. */
    public String jeton(String sel) {
        try {
            Mac mac = Mac.getInstance(ALGORITHME);
            mac.init(cle);
            byte[] signature = mac.doFinal(sel.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }

    /** SHA-256 du jeton (64 caractères hexadécimaux), seule forme gardée en base. */
    public String hash(String jeton) {
        return HachageJeton.hacher(jeton);
    }

    /** Hash du jeton calculé depuis le sel : à enregistrer à la création (ou au changement de sel). */
    public String hashDepuisSel(String sel) {
        return hash(jeton(sel));
    }
}
