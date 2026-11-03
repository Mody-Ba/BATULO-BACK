package com.batulo.configs;

import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.NormaliseurCourriel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/**
 * Crée le super admin (propriétaire de Batulo) au démarrage, à partir des variables SUPER_ADMIN_EMAIL et
 * SUPER_ADMIN_PASSWORD lues dans .env.properties ou l'environnement : aucune valeur dans le code ni dans
 * application.yml. Ne fait rien s'il existe déjà un super admin (le compte en base n'est jamais modifié).
 * Si une variable manque, écrit un avertissement et laisse l'application démarrer.
 * Le mot de passe est chiffré avec BCrypt et n'est jamais écrit dans les logs.
 * Aucune route de l'API ne permet de créer un super admin.
 */
@Component
public class SuperAdminInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminInitializer.class);

    /** Limite de BCrypt : au-delà, les octets suivants seraient ignorés. */
    private static final int TAILLE_MAX_MOT_DE_PASSE_OCTETS = 72;

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    // ":" sans valeur : une variable absente donne une chaîne vide au lieu d'empêcher le démarrage.
    @Value("${SUPER_ADMIN_EMAIL:}")
    private String superAdminEmail;

    @Value("${SUPER_ADMIN_PASSWORD:}")
    private String superAdminPassword;

    public SuperAdminInitializer(UtilisateurRepository utilisateurRepository, PasswordEncoder passwordEncoder) {
        this.utilisateurRepository = utilisateurRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        boolean variablesPresentes = superAdminEmail != null && !superAdminEmail.isBlank()
                && superAdminPassword != null && !superAdminPassword.isBlank();

        if (utilisateurRepository.existsByRole(Role.SUPER_ADMIN)) {
            log.info("Super admin déjà présent en base : aucune modification.");
            return;
        }
        if (!variablesPresentes) {
            log.warn("Aucun super admin en base et SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD absentes : "
                    + "aucun super admin créé. Renseignez-les dans .env.properties puis redémarrez.");
            return;
        }
        if (superAdminPassword.getBytes(StandardCharsets.UTF_8).length > TAILLE_MAX_MOT_DE_PASSE_OCTETS) {
            log.warn("SUPER_ADMIN_PASSWORD dépasse {} octets : aucun super admin créé.", TAILLE_MAX_MOT_DE_PASSE_OCTETS);
            return;
        }

        String courriel = NormaliseurCourriel.normaliser(superAdminEmail);
        if (utilisateurRepository.existsByCourriel(courriel)) {
            log.warn("SUPER_ADMIN_EMAIL correspond déjà à un compte admin ou gestionnaire : aucun super admin créé.");
            return;
        }

        Utilisateur superAdmin = new Utilisateur();
        superAdmin.setPrenom("Super");
        superAdmin.setNom("Admin");
        superAdmin.setCourriel(courriel);
        superAdmin.setMotDePasse(passwordEncoder.encode(superAdminPassword));
        superAdmin.setRole(Role.SUPER_ADMIN);
        superAdmin.setActif(true);
        superAdmin = utilisateurRepository.save(superAdmin);

        log.info("Super admin créé à partir de SUPER_ADMIN_EMAIL : compte {}", superAdmin.getId());
    }
}
