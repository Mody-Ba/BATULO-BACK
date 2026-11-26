package com.batulo.service;

import com.batulo.entity.Abonnement;
import com.batulo.entity.Entreprise;
import com.batulo.entity.Paiement;
import com.batulo.enums.Role;
import com.batulo.enums.StatutPaiement;
import com.batulo.enums.TypeNotification;
import com.batulo.enums.TypePaiement;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.email.EmailService;
import com.batulo.service.model.CreditsModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Seule façon d'activer ou de prolonger un abonnement, ou de créditer un achat de sondages, après un paiement (RG-09), commune à Stripe (confirmation)
 * et à Moosyl (webhook, relecture du statut). Idempotente : un paiement déjà REUSSI ne change plus rien, donc un
 * paiement ne prolonge jamais l'abonnement deux fois.
 *
 * <p>Le paiement doit avoir été chargé avec un verrou d'écriture (PaiementRepository.verrouiller...) dans la
 * transaction de l'appelant : deux appels simultanés pour le même paiement s'exécutent l'un après l'autre.
 */
@Component
public class ActivationPaiement {

    private static final Logger log = LoggerFactory.getLogger(ActivationPaiement.class);

    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final FormatDate formatDate;
    private final CreditsSondages creditsSondages;

    @Value("${batulo.abonnement.jours-alerte-expiration}")
    private int joursAlerte;

    public ActivationPaiement(UtilisateurRepository utilisateurRepository, NotificationService notificationService,
                              EmailService emailService, FormatDate formatDate, CreditsSondages creditsSondages) {
        this.utilisateurRepository = utilisateurRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.formatDate = formatDate;
        this.creditsSondages = creditsSondages;
    }

    /**
     * Marque le paiement REUSSI et active ou prolonge l'abonnement, puis avertit les super admins et l'admin.
     *
     * @param paiementVerrouille paiement chargé avec un verrou d'écriture
     * @param source             origine de l'activation, pour les journaux (« confirmation Stripe », « webhook »...)
     * @return false si le paiement était déjà REUSSI (rien n'a changé)
     */
    public boolean activer(Paiement paiementVerrouille, Instant maintenant, String source) {
        if (paiementVerrouille.getStatut() == StatutPaiement.REUSSI) {
            log.info("Paiement {} déjà réussi ({}) : rien ne change", paiementVerrouille.getId(), source);
            return false;
        }
        if (paiementVerrouille.getType() == TypePaiement.ACHAT_SONDAGES) {
            crediterAchat(paiementVerrouille, maintenant, source);
            return true;
        }
        Abonnement abonnement = paiementVerrouille.getAbonnement();
        // Renouvellement : l'entreprise avait déjà payé une période (en cours ou expirée).
        boolean renouvellement = abonnement.getDateExpiration() != null;
        Instant debut = RegleAbonnement.debutNouvellePeriode(abonnement, maintenant);
        if (debut.equals(maintenant)) {
            abonnement.setDateDebut(maintenant);
        }
        abonnement.setDateExpiration(RegleAbonnement.ajouterDuree(paiementVerrouille.getFormule(), debut));
        abonnement.setFormule(paiementVerrouille.getFormule());
        abonnement.setMontant(paiementVerrouille.getMontant());
        abonnement.setDevise(paiementVerrouille.getDevise());
        abonnement.setNotif7jEnvoyee(false);
        abonnement.setNotifExpirationEnvoyee(false);
        abonnement.setStatut(RegleAbonnement.statutEffectif(abonnement, maintenant, joursAlerte));

        paiementVerrouille.setStatut(StatutPaiement.REUSSI);
        paiementVerrouille.setDatePaiement(maintenant);

        avertir(abonnement, paiementVerrouille, renouvellement);
        log.info("Paiement {} ({}, {}) réussi : abonnement {} {} jusqu'au {}", paiementVerrouille.getId(),
                paiementVerrouille.getFournisseur(), source, abonnement.getId(), abonnement.getStatut(),
                abonnement.getDateExpiration());
        return true;
    }

    /** Achat de sondages : le solde augmente de la quantité payée (l'abonnement ne change pas). */
    private void crediterAchat(Paiement paiement, Instant maintenant, String source) {
        Abonnement abonnement = paiement.getAbonnement();
        Entreprise entreprise = abonnement.getEntreprise();
        int quantite = paiement.getQuantite();
        paiement.setStatut(StatutPaiement.REUSSI);
        paiement.setDatePaiement(maintenant);
        CreditsModel credits = creditsSondages.crediter(entreprise, quantite, maintenant);

        notificationService.notifierSuperAdmins(TypeNotification.PAIEMENT_RECU, "Paiement reçu",
                entreprise.getNom() + " a payé " + paiement.getMontant().toPlainString() + " " + paiement.getDevise()
                        + " (achat de " + quantite + " sondage(s)). Solde : " + credits.solde() + " sondage(s).",
                abonnement.getId(), null);
        utilisateurRepository.findFirstByEntrepriseIdAndRole(entreprise.getId(), Role.ADMIN)
                .ifPresent(admin -> emailService.envoyerConfirmationAchatSondages(admin.getCourriel(),
                        admin.getPrenom(), quantite, paiement.getMontant(), paiement.getDevise(), credits.solde()));
        log.info("Paiement {} ({}, {}) réussi : {} sondage(s) pour l'entreprise {}, solde {}", paiement.getId(),
                paiement.getFournisseur(), source, quantite, entreprise.getId(), credits.solde());
    }

    /** Courriel de confirmation à l'admin et notification aux super admins (envoyés après validation). */
    private void avertir(Abonnement abonnement, Paiement paiement, boolean renouvellement) {
        String entreprise = abonnement.getEntreprise().getNom();
        notificationService.notifierSuperAdmins(TypeNotification.PAIEMENT_RECU, "Paiement reçu",
                entreprise + " a payé " + paiement.getMontant().toPlainString() + " " + paiement.getDevise()
                        + " (formule " + paiement.getFormule() + (renouvellement ? ", renouvellement" : "")
                        + "). Abonnement valable jusqu'au " + formatDate.jour(abonnement.getDateExpiration()) + ".",
                abonnement.getId(), null);
        utilisateurRepository.findFirstByEntrepriseIdAndRole(abonnement.getEntreprise().getId(), Role.ADMIN)
                .ifPresent(admin -> emailService.envoyerConfirmationPaiement(admin.getCourriel(), admin.getPrenom(),
                        paiement.getFormule(), paiement.getMontant(), paiement.getDevise(),
                        abonnement.getDateExpiration(), renouvellement));
    }
}
