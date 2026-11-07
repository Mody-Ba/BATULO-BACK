package com.batulo.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Jetons opaques (rafraîchissement, réinitialisation) : seul leur hash SHA-256 est stocké en base,
 * pour qu'une fuite de la base ne donne aucun jeton utilisable.
 */
final class HachageJeton {

    private static final SecureRandom ALEATOIRE = new SecureRandom();

    private HachageJeton() {
    }

    /** 256 bits aléatoires, encodés en Base64 URL (sans remplissage). */
    static String nouveauJeton() {
        byte[] octets = new byte[32];
        ALEATOIRE.nextBytes(octets);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
    }

    /** SHA-256 en hexadécimal (64 caractères, taille de la colonne jeton_hash). */
    static String hacher(String jeton) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(jeton.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
