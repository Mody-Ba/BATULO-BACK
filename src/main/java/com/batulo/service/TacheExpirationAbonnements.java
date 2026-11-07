package com.batulo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Déclenche la tâche des abonnements chaque jour (batulo.abonnement.cron-expiration, 8 h par défaut, heure de
 * batulo.fuseau-horaire) et une fois au démarrage, pour rattraper un passage manqué pendant un arrêt.
 */
@Component
public class TacheExpirationAbonnements {

    private static final Logger log = LoggerFactory.getLogger(TacheExpirationAbonnements.class);

    private final ExpirationAbonnementService expirationAbonnementService;

    public TacheExpirationAbonnements(ExpirationAbonnementService expirationAbonnementService) {
        this.expirationAbonnementService = expirationAbonnementService;
    }

    @Scheduled(cron = "${batulo.abonnement.cron-expiration}", zone = "${batulo.fuseau-horaire}")
    public void executerChaqueJour() {
        executer("passage quotidien");
    }

    /** Après le démarrage complet (le super admin existe déjà et peut recevoir les notifications). */
    @EventListener(ApplicationReadyEvent.class)
    public void executerAuDemarrage() {
        executer("démarrage");
    }

    private void executer(String declencheur) {
        log.info("Tâche des abonnements : {}", declencheur);
        try {
            expirationAbonnementService.verifierAbonnements();
        } catch (RuntimeException e) {
            // Ne doit ni empêcher le démarrage ni arrêter la planification.
            log.error("Tâche des abonnements ({}) en échec", declencheur, e);
        }
    }
}
