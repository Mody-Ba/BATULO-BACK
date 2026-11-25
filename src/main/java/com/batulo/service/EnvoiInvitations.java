package com.batulo.service;

import com.batulo.configs.InvitationConfig;
import com.batulo.entity.Invitation;
import com.batulo.entity.InvitationEnvoi;
import com.batulo.entity.Sondage;
import com.batulo.enums.Langue;
import com.batulo.enums.StatutSondage;
import com.batulo.enums.TypeEnvoiInvitation;
import com.batulo.repository.InvitationEnvoiRepository;
import com.batulo.repository.InvitationRepository;
import com.batulo.repository.SondageRepository;
import com.batulo.service.email.EmailService;
import com.batulo.service.email.MessageInvitation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Envoi des courriels d'invitation en arrière-plan. La requête réserve les invitations (envoi_en_file_le) dans sa
 * transaction ; après sa validation, chaque courriel part dans l'exécuteur dédié : lecture (transaction courte),
 * envoi SMTP hors transaction, puis enregistrement du résultat et du journal (transaction courte).
 */
@Component
public class EnvoiInvitations {

    private static final Logger log = LoggerFactory.getLogger(EnvoiInvitations.class);

    private final InvitationRepository invitationRepository;
    private final InvitationEnvoiRepository envoiRepository;
    private final SondageRepository sondageRepository;
    private final EmailService emailService;
    private final JetonsInvitation jetons;
    private final Personnalisations personnalisations;
    private final TaskExecutor executeur;
    private final TransactionTemplate transaction;
    private final String urlFormulaire;

    public EnvoiInvitations(InvitationRepository invitationRepository, InvitationEnvoiRepository envoiRepository,
                            SondageRepository sondageRepository, EmailService emailService, JetonsInvitation jetons,
                            Personnalisations personnalisations,
                            @Qualifier(InvitationConfig.EXECUTEUR) TaskExecutor executeur,
                            PlatformTransactionManager transactionManager,
                            @Value("${batulo.collecte.url-formulaire}") String urlFormulaire) {
        this.invitationRepository = invitationRepository;
        this.envoiRepository = envoiRepository;
        this.sondageRepository = sondageRepository;
        this.emailService = emailService;
        this.jetons = jetons;
        this.personnalisations = personnalisations;
        this.executeur = executeur;
        this.transaction = new TransactionTemplate(transactionManager);
        // Toujours une transaction à part : appelé aussi depuis afterCommit (exécuteur synchrone des tests).
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.urlFormulaire = urlFormulaire;
    }

    /** Lien personnel complet : page du formulaire, jeton du sondage (lignée) et jeton de l'invitation. */
    String lien(Sondage racine, Invitation invitation) {
        return urlFormulaire + "/" + racine.getJetonPublic() + "?i=" + jetons.jeton(invitation.getJetonSel());
    }

    /**
     * Version qui reçoit les réponses (EN_COURS), sinon la prochaine planifiée : c'est elle que le lien ouvrira.
     * Vide si la lignée n'a ni version ouverte ni version planifiée.
     */
    Optional<Sondage> versionCible(Long ligneeId, Instant maintenant) {
        List<Sondage> lignee = sondageRepository.findLignee(ligneeId);
        Optional<Sondage> ouverte = lignee.stream()
                .filter(v -> RegleSondage.statut(v, maintenant) == StatutSondage.EN_COURS)
                .max(Comparator.comparingInt(Sondage::getVersion));
        if (ouverte.isPresent()) {
            return ouverte;
        }
        return lignee.stream()
                .filter(v -> v.isPublie() && RegleSondage.statut(v, maintenant) == StatutSondage.NON_COMMENCE)
                .min(Comparator.comparing(Sondage::getDatePublication));
    }

    /** À appeler dans la transaction qui a réservé les invitations : les envois partent après sa validation. */
    void planifier(List<Long> invitationIds, TypeEnvoiInvitation type) {
        if (invitationIds.isEmpty()) {
            return;
        }
        Runnable travail = () -> invitationIds.forEach(id -> envoyerUne(id, type));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    executeur.execute(travail);
                }
            });
        } else {
            executeur.execute(travail);
        }
    }

    private record Preparation(Long entrepriseId, String courriel, MessageInvitation message) {
    }

    private void envoyerUne(Long invitationId, TypeEnvoiInvitation type) {
        try {
            Preparation preparation = transaction.execute(statut -> preparer(invitationId, type));
            if (preparation == null) {
                return;
            }
            boolean succes = emailService.envoyerInvitation(preparation.courriel(), preparation.message());
            transaction.executeWithoutResult(statut -> enregistrer(invitationId, preparation.entrepriseId(), type,
                    succes));
        } catch (RuntimeException e) {
            // Une invitation en erreur n'arrête jamais les suivantes ; elle reste « à inviter » ou « en échec ».
            log.error("Envoi de l'invitation {} impossible : {}", invitationId, e.getClass().getSimpleName());
            transaction.executeWithoutResult(statut -> invitationRepository.findById(invitationId)
                    .ifPresent(i -> i.setEnvoiEnFileLe(null)));
        }
    }

    /** Lit tout ce qu'il faut pour le courriel ; null (et la réservation est levée) si l'envoi n'a plus lieu d'être. */
    private Preparation preparer(Long invitationId, TypeEnvoiInvitation type) {
        Invitation invitation = invitationRepository.findById(invitationId).orElse(null);
        if (invitation == null) {
            return null;
        }
        Sondage racine = invitation.getLignee();
        Optional<Sondage> version = versionCible(racine.getId(), Instant.now());
        if (invitation.getRevoqueeLe() != null || invitation.getReponduLe() != null || version.isEmpty()) {
            invitation.setEnvoiEnFileLe(null);
            return null;
        }
        Sondage cible = version.get();
        // Langue du courriel : celle de l'invité, sinon la langue principale de la version ; le titre suit si le
        // sondage la propose (texte principal en secours).
        Langue langue = invitation.getLangue() != null ? invitation.getLangue() : cible.getLanguePrincipale();
        String titre = Traducteur.pour(cible, langue).titre(cible);
        MessageInvitation message = new MessageInvitation(langue, invitation.getPrenom(), titre,
                personnalisations.nomEffectif(racine.getEntreprise()), lien(racine, invitation),
                cible.getDateFermeture(), type == TypeEnvoiInvitation.RELANCE);
        return new Preparation(racine.getEntreprise().getId(), invitation.getCourriel(), message);
    }

    private void enregistrer(Long invitationId, Long entrepriseId, TypeEnvoiInvitation type, boolean succes) {
        Instant maintenant = Instant.now();
        envoiRepository.save(new InvitationEnvoi(entrepriseId, invitationId, type, maintenant, succes));
        invitationRepository.findById(invitationId).ifPresent(invitation -> {
            invitation.setEnvoiEnFileLe(null);
            if (succes) {
                if (invitation.getPremierEnvoiLe() == null) {
                    invitation.setPremierEnvoiLe(maintenant);
                }
                invitation.setDernierEnvoiLe(maintenant);
                if (type == TypeEnvoiInvitation.RELANCE) {
                    invitation.setDerniereRelanceLe(maintenant);
                }
                invitation.setNombreEnvois(invitation.getNombreEnvois() + 1);
                invitation.setEchecEnvoiLe(null);
            } else {
                invitation.setEchecEnvoiLe(maintenant);
            }
        });
        log.info("Invitation {} : {} {}", invitationId, type == TypeEnvoiInvitation.RELANCE ? "relance" : "invitation",
                succes ? "envoyée" : "en échec");
    }
}
