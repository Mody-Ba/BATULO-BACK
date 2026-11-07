package com.batulo.service;

import com.batulo.entity.Utilisateur;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Attribue un mot de passe temporaire à un compte (admin ou gestionnaire) : généré aléatoirement, chiffré avec
 * BCrypt, valide 24 h (RG-06) et à changer à la première connexion. Partagé par le super admin et l'admin.
 */
@Component
public class MotDePasseTemporaire {

    private final GenerateurMotDePasse generateurMotDePasse;
    private final PasswordEncoder passwordEncoder;

    @Value("${batulo.mot-de-passe.duree-temporaire-heures}")
    private long dureeTemporaireHeures;

    public MotDePasseTemporaire(GenerateurMotDePasse generateurMotDePasse, PasswordEncoder passwordEncoder) {
        this.generateurMotDePasse = generateurMotDePasse;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Remplace le mot de passe du compte (l'ancien ne fonctionne plus) et renvoie le nouveau en clair,
     * uniquement pour le courriel : il ne doit jamais être renvoyé par l'API.
     */
    public String attribuer(Utilisateur utilisateur) {
        String motDePasse = generateurMotDePasse.generer();
        utilisateur.setMotDePasse(passwordEncoder.encode(motDePasse));
        utilisateur.setMotDePasseTemporaire(true);
        utilisateur.setMotDePasseTemporaireExpireLe(Instant.now().plus(Duration.ofHours(dureeTemporaireHeures)));
        return motDePasse;
    }
}
