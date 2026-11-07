package com.batulo.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * TEMPORAIRE - remplacé à l'étape 6 par l'envoi SMTP réel (SmtpEmailService).
 * En profil dev, écrit le mot de passe temporaire dans les logs pour pouvoir tester ; dans tout autre profil,
 * le mot de passe n'est jamais écrit et un avertissement signale que le courriel n'est pas encore envoyé.
 */
@Service
public class JournalEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(JournalEmailService.class);

    private final Environment environment;

    public JournalEmailService(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void envoyerMotDePasseTemporaire(String courriel, String prenom, String motDePasse, Instant expireLe) {
        if (environment.matchesProfiles("dev")) {
            log.info("[DEV - courriel non envoyé avant l'étape 6] Mot de passe temporaire de {} : {} (valide jusqu'au {})",
                    courriel, motDePasse, expireLe);
        } else {
            log.warn("Envoi de courriel pas encore disponible (étape 6) : mot de passe temporaire de {} non communiqué",
                    courriel);
        }
    }
}
