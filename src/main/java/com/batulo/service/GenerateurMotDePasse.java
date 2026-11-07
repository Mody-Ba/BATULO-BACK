package com.batulo.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mot de passe temporaire aléatoire (SecureRandom) de 12 caractères, avec au moins une majuscule,
 * une minuscule, un chiffre et un caractère spécial. Les caractères faciles à confondre
 * (0/O, 1/l/I) sont exclus pour que l'admin puisse le recopier depuis le courriel.
 */
@Component
public class GenerateurMotDePasse {

    static final int LONGUEUR = 12;

    private static final String MAJUSCULES = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String MINUSCULES = "abcdefghijkmnopqrstuvwxyz";
    private static final String CHIFFRES = "23456789";
    private static final String SPECIAUX = "#$%&*+-=?@";
    private static final String TOUS = MAJUSCULES + MINUSCULES + CHIFFRES + SPECIAUX;

    private final SecureRandom aleatoire = new SecureRandom();

    public String generer() {
        List<Character> caracteres = new ArrayList<>(LONGUEUR);
        caracteres.add(tirer(MAJUSCULES));
        caracteres.add(tirer(MINUSCULES));
        caracteres.add(tirer(CHIFFRES));
        caracteres.add(tirer(SPECIAUX));
        while (caracteres.size() < LONGUEUR) {
            caracteres.add(tirer(TOUS));
        }
        Collections.shuffle(caracteres, aleatoire);

        StringBuilder motDePasse = new StringBuilder(LONGUEUR);
        caracteres.forEach(motDePasse::append);
        return motDePasse.toString();
    }

    private char tirer(String alphabet) {
        return alphabet.charAt(aleatoire.nextInt(alphabet.length()));
    }
}
