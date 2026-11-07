package com.batulo.service;

import com.batulo.entity.Abonnement;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import com.batulo.enums.StatutAbonnement;
import com.batulo.enums.TypeNotification;
import com.batulo.repository.AbonnementRepository;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.email.EmailService;
import com.batulo.service.model.ResultatTacheModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class ExpirationAbonnementServiceImpl implements ExpirationAbonnementService {

    private static final Logger log = LoggerFactory.getLogger(ExpirationAbonnementServiceImpl.class);

    private enum Resultat { RAPPEL, EXPIRATION, AUCUN }

    private final AbonnementRepository abonnementRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final FormatDate formatDate;
    private final TransactionTemplate transaction;

    @Value("${batulo.abonnement.jours-alerte-expiration}")
    private int joursAlerte;

    public ExpirationAbonnementServiceImpl(AbonnementRepository abonnementRepository,
                                           UtilisateurRepository utilisateurRepository,
                                           NotificationService notificationService,
                                           EmailService emailService,
                                           FormatDate formatDate,
                                           PlatformTransactionManager transactionManager) {
        this.abonnementRepository = abonnementRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.formatDate = formatDate;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * synchronized : le passage planifié, celui du démarrage et la route de dev ne se chevauchent pas.
     * Chaque abonnement est traité dans sa propre transaction : une erreur sur l'un n'empêche pas les autres,
     * et ses courriels ne partent qu'une fois sa transaction validée.
     */
    @Override
    public synchronized ResultatTacheModel verifierAbonnements() {
        Instant maintenant = Instant.now();
        List<Long> candidats = transaction.execute(statut ->
                abonnementRepository.findCandidatsAlerteExpiration().stream().map(Abonnement::getId).toList());

        int rappels = 0;
        int expirations = 0;
        for (Long abonnementId : candidats) {
            try {
                Resultat resultat = transaction.execute(statut -> traiter(abonnementId, maintenant));
                if (resultat == Resultat.RAPPEL) {
                    rappels++;
                } else if (resultat == Resultat.EXPIRATION) {
                    expirations++;
                }
            } catch (RuntimeException e) {
                log.error("Tâche des abonnements : échec sur l'abonnement {}, on continue avec les suivants",
                        abonnementId, e);
            }
        }
        log.info("Tâche des abonnements : {} vérifié(s), {} rappel(s), {} expiration(s)",
                candidats.size(), rappels, expirations);
        return new ResultatTacheModel(candidats.size(), rappels, expirations);
    }

    private Resultat traiter(Long abonnementId, Instant maintenant) {
        Abonnement abonnement = abonnementRepository.findWithEntrepriseById(abonnementId).orElse(null);
        // Revérifié dans la transaction : l'abonnement a pu être payé, désactivé ou supprimé entre-temps.
        if (abonnement == null || !abonnement.getEntreprise().isActif() || abonnement.getDateExpiration() == null
                || abonnement.isNotifExpirationEnvoyee()) {
            return Resultat.AUCUN;
        }
        Instant expiration = abonnement.getDateExpiration();
        String entreprise = abonnement.getEntreprise().getNom();
        Optional<Utilisateur> admin = utilisateurRepository.findFirstByEntrepriseIdAndRole(
                abonnement.getEntreprise().getId(), Role.ADMIN);

        if (!maintenant.isBefore(expiration)) {
            abonnement.setStatut(StatutAbonnement.EXPIRE);
            abonnement.setNotifExpirationEnvoyee(true);
            // Si le rappel n'a pas pu partir (application arrêtée), il ne partira plus pour cette période.
            abonnement.setNotif7jEnvoyee(true);
            String date = formatDate.jour(expiration);
            admin.ifPresent(a -> {
                notificationService.notifier(a.getId(), TypeNotification.ABONNEMENT_EXPIRE, "Abonnement expiré",
                        "Votre abonnement a expiré le " + date + ". Votre espace est en lecture seule : "
                                + "renouvelez-le pour retrouver l'accès complet.");
                emailService.envoyerAbonnementExpire(a.getCourriel(), a.getPrenom(), entreprise, expiration);
            });
            notificationService.notifierSuperAdmins(TypeNotification.ABONNEMENT_EXPIRE, "Abonnement expiré",
                    "L'abonnement de " + entreprise + " a expiré le " + date + ".");
            log.info("Abonnement {} expiré", abonnementId);
            return Resultat.EXPIRATION;
        }

        if (!abonnement.isNotif7jEnvoyee()
                && !expiration.isAfter(maintenant.plus(Duration.ofDays(joursAlerte)))) {
            abonnement.setStatut(StatutAbonnement.EXPIRE_BIENTOT);
            abonnement.setNotif7jEnvoyee(true);
            long jours = RegleAbonnement.joursRestants(abonnement, maintenant);
            String date = formatDate.jour(expiration);
            admin.ifPresent(a -> {
                notificationService.notifier(a.getId(), TypeNotification.ABONNEMENT_EXPIRE_BIENTOT,
                        "Abonnement bientôt expiré",
                        "Votre abonnement expire dans " + jours + " jour(s), le " + date + ". Renouvelez-le pour "
                                + "garder l'accès complet.");
                emailService.envoyerRappelExpiration(a.getCourriel(), a.getPrenom(), entreprise, expiration, jours);
            });
            notificationService.notifierSuperAdmins(TypeNotification.ABONNEMENT_EXPIRE_BIENTOT,
                    "Abonnement bientôt expiré",
                    "L'abonnement de " + entreprise + " expire dans " + jours + " jour(s), le " + date + ".");
            log.info("Abonnement {} : rappel d'expiration envoyé ({} jour(s))", abonnementId, jours);
            return Resultat.RAPPEL;
        }

        // Rien à signaler : on garde seulement le statut enregistré en phase avec les dates.
        abonnement.setStatut(RegleAbonnement.statutEffectif(abonnement, maintenant, joursAlerte));
        return Resultat.AUCUN;
    }
}
