package com.batulo.service;

import com.batulo.entity.Abonnement;
import com.batulo.entity.Paiement;
import com.batulo.enums.Formule;
import com.batulo.enums.Role;
import com.batulo.enums.TypeNotification;
import com.batulo.enums.StatutPaiement;
import com.batulo.exception.RegleGestionException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.AbonnementRepository;
import com.batulo.repository.PaiementRepository;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.email.EmailService;
import com.batulo.service.mappers.AbonnementMapper;
import com.batulo.service.model.AbonnementModel;
import com.batulo.service.model.ConfirmationPaiementModel;
import com.batulo.service.model.DemandeIntentionPaiementModel;
import com.batulo.service.model.FormuleModel;
import com.batulo.service.model.IntentionPaiementModel;
import com.batulo.service.model.IntentionStripeModel;
import com.batulo.service.model.PaiementConfirmeModel;
import com.batulo.service.paiement.PaiementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AbonnementServiceImpl implements AbonnementService {

    private static final Logger log = LoggerFactory.getLogger(AbonnementServiceImpl.class);

    /** Statut Stripe d'un PaymentIntent payé. */
    private static final String STRIPE_REUSSI = "succeeded";

    /** Clés des métadonnées recopiées chez Stripe et revérifiées à la confirmation. */
    static final String META_ENTREPRISE = "entrepriseId";
    static final String META_ABONNEMENT = "abonnementId";
    static final String META_FORMULE = "formule";

    private final AbonnementRepository abonnementRepository;
    private final PaiementRepository paiementRepository;
    private final AbonnementMapper abonnementMapper;
    private final PaiementService paiementService;
    private final UtilisateurRepository utilisateurRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final FormatDate formatDate;

    @Value("${batulo.abonnement.devise}")
    private String devise;

    @Value("${batulo.abonnement.prix-mensuel-centimes}")
    private long prixMensuelCentimes;

    @Value("${batulo.abonnement.prix-annuel-centimes}")
    private long prixAnnuelCentimes;

    @Value("${batulo.abonnement.jours-alerte-expiration}")
    private int joursAlerte;

    public AbonnementServiceImpl(AbonnementRepository abonnementRepository,
                                 PaiementRepository paiementRepository,
                                 AbonnementMapper abonnementMapper,
                                 PaiementService paiementService,
                                 UtilisateurRepository utilisateurRepository,
                                 NotificationService notificationService,
                                 EmailService emailService,
                                 FormatDate formatDate) {
        this.abonnementRepository = abonnementRepository;
        this.paiementRepository = paiementRepository;
        this.abonnementMapper = abonnementMapper;
        this.paiementService = paiementService;
        this.utilisateurRepository = utilisateurRepository;
        this.notificationService = notificationService;
        this.emailService = emailService;
        this.formatDate = formatDate;
    }

    @Override
    public List<FormuleModel> formules() {
        return Arrays.stream(Formule.values())
                .map(formule -> new FormuleModel(formule, libelle(formule), montant(prixCentimes(formule)),
                        deviseAffichee(), duree(formule)))
                .toList();
    }

    @Override
    public AbonnementModel monAbonnement(Long entrepriseId) {
        return versModele(abonnementDe(entrepriseId), Instant.now());
    }

    @Override
    @Transactional
    public IntentionPaiementModel creerIntentionPaiement(Long entrepriseId, DemandeIntentionPaiementModel demande) {
        Abonnement abonnement = abonnementDe(entrepriseId);
        if (!abonnement.getEntreprise().isActif()) {
            throw new RegleGestionException("ENTREPRISE_DESACTIVEE",
                    "Votre entreprise a été désactivée : le paiement est impossible. Contactez Batulo.");
        }
        Formule formule = demande.formule();
        long centimes = prixCentimes(formule);
        String clePublique = paiementService.clePublique();

        IntentionStripeModel intention = paiementService.creerIntention(centimes, devise,
                "Batulo – " + libelle(formule) + " – " + abonnement.getEntreprise().getNom(),
                Map.of(META_ENTREPRISE, String.valueOf(entrepriseId),
                        META_ABONNEMENT, String.valueOf(abonnement.getId()),
                        META_FORMULE, formule.name()));

        Paiement paiement = new Paiement();
        paiement.setAbonnement(abonnement);
        paiement.setStripePaymentIntentId(intention.id());
        paiement.setFormule(formule);
        paiement.setMontant(montant(centimes));
        paiement.setDevise(deviseAffichee());
        paiement.setStatut(StatutPaiement.EN_ATTENTE);
        paiementRepository.save(paiement);

        log.info("Intention de paiement {} créée pour l'abonnement {} ({})",
                intention.id(), abonnement.getId(), formule);
        return new IntentionPaiementModel(intention.id(), intention.clientSecret(), clePublique, formule,
                paiement.getMontant(), paiement.getDevise());
    }

    @Override
    @Transactional
    public PaiementConfirmeModel confirmerPaiement(Long entrepriseId, ConfirmationPaiementModel confirmation) {
        String paymentIntentId = confirmation.paymentIntentId();
        // Verrou sur la ligne : deux confirmations simultanées ne prolongent l'abonnement qu'une fois.
        Paiement paiement = paiementRepository.verrouillerParStripePaymentIntentId(paymentIntentId)
                .filter(p -> p.getAbonnement().getEntreprise().getId().equals(entrepriseId))
                .orElseThrow(() -> new ResourceNotFoundException("PAIEMENT_INTROUVABLE",
                        "Aucun paiement de votre entreprise ne correspond à ce paymentIntentId."));
        Abonnement abonnement = paiement.getAbonnement();
        Instant maintenant = Instant.now();

        if (paiement.getStatut() == StatutPaiement.REUSSI) {
            return new PaiementConfirmeModel(versModele(abonnement, maintenant), true);
        }

        // On ne fait jamais confiance au frontend : Stripe est relu pour savoir si le paiement a bien eu lieu.
        IntentionStripeModel intention = paiementService.recupererIntention(paymentIntentId);
        verifierCoherence(intention, paiement, entrepriseId);
        if (!STRIPE_REUSSI.equals(intention.statut())) {
            throw new RegleGestionException("PAIEMENT_NON_REUSSI",
                    "Stripe n'a pas validé ce paiement (statut : " + intention.statut() + "). "
                            + "L'abonnement n'est pas modifié.");
        }

        // Renouvellement : l'entreprise avait déjà payé une période (en cours ou expirée).
        boolean renouvellement = abonnement.getDateExpiration() != null;
        Instant debut = RegleAbonnement.debutNouvellePeriode(abonnement, maintenant);
        if (debut.equals(maintenant)) {
            abonnement.setDateDebut(maintenant);
        }
        abonnement.setDateExpiration(RegleAbonnement.ajouterDuree(paiement.getFormule(), debut));
        abonnement.setFormule(paiement.getFormule());
        abonnement.setMontant(paiement.getMontant());
        abonnement.setDevise(paiement.getDevise());
        abonnement.setNotif7jEnvoyee(false);
        abonnement.setNotifExpirationEnvoyee(false);
        abonnement.setStatut(RegleAbonnement.statutEffectif(abonnement, maintenant, joursAlerte));

        paiement.setStatut(StatutPaiement.REUSSI);
        paiement.setDatePaiement(maintenant);

        avertirPaiement(abonnement, paiement, renouvellement);
        log.info("Paiement {} confirmé : abonnement {} {} jusqu'au {}",
                paymentIntentId, abonnement.getId(), abonnement.getStatut(), abonnement.getDateExpiration());
        return new PaiementConfirmeModel(versModele(abonnement, maintenant), false);
    }

    /** Courriel de confirmation à l'admin et notification aux super admins (envoyés après validation). */
    private void avertirPaiement(Abonnement abonnement, Paiement paiement, boolean renouvellement) {
        String entreprise = abonnement.getEntreprise().getNom();
        notificationService.notifierSuperAdmins(TypeNotification.PAIEMENT_RECU, "Paiement reçu",
                entreprise + " a payé " + paiement.getMontant().toPlainString() + " " + paiement.getDevise()
                        + " (formule " + paiement.getFormule() + (renouvellement ? ", renouvellement" : "")
                        + "). Abonnement valable jusqu'au " + formatDate.jour(abonnement.getDateExpiration()) + ".");
        utilisateurRepository.findFirstByEntrepriseIdAndRole(abonnement.getEntreprise().getId(), Role.ADMIN)
                .ifPresent(admin -> emailService.envoyerConfirmationPaiement(admin.getCourriel(), admin.getPrenom(),
                        paiement.getFormule(), paiement.getMontant(), paiement.getDevise(),
                        abonnement.getDateExpiration(), renouvellement));
    }

    /** Le PaymentIntent doit être celui créé pour cette entreprise, avec le montant et la devise attendus. */
    private static void verifierCoherence(IntentionStripeModel intention, Paiement paiement, Long entrepriseId) {
        boolean memeEntreprise = String.valueOf(entrepriseId).equals(intention.metadonnees().get(META_ENTREPRISE));
        boolean memeMontant = intention.montantCentimes() != null
                && paiement.getMontant().movePointRight(2).longValueExact() == intention.montantCentimes();
        boolean memeDevise = paiement.getDevise().equalsIgnoreCase(intention.devise());
        if (!memeEntreprise || !memeMontant || !memeDevise) {
            log.warn("PaymentIntent {} incohérent avec le paiement enregistré (entreprise {}, montant {}, devise {})",
                    intention.id(), memeEntreprise, memeMontant, memeDevise);
            throw new RegleGestionException("PAIEMENT_INVALIDE",
                    "Ce paiement Stripe ne correspond pas à l'abonnement de votre entreprise.");
        }
    }

    private Abonnement abonnementDe(Long entrepriseId) {
        return abonnementRepository.findByEntrepriseId(entrepriseId)
                .orElseThrow(() -> new ResourceNotFoundException("ABONNEMENT_INTROUVABLE",
                        "Aucun abonnement pour l'entreprise " + entrepriseId + "."));
    }

    private AbonnementModel versModele(Abonnement abonnement, Instant maintenant) {
        return abonnementMapper.toModel(abonnement,
                RegleAbonnement.statutEffectif(abonnement, maintenant, joursAlerte),
                RegleAbonnement.joursRestants(abonnement, maintenant));
    }

    private long prixCentimes(Formule formule) {
        return switch (formule) {
            case MENSUEL -> prixMensuelCentimes;
            case ANNUEL -> prixAnnuelCentimes;
        };
    }

    private static String libelle(Formule formule) {
        return switch (formule) {
            case MENSUEL -> "Abonnement mensuel";
            case ANNUEL -> "Abonnement annuel";
        };
    }

    private static String duree(Formule formule) {
        return switch (formule) {
            case MENSUEL -> "1 mois";
            case ANNUEL -> "1 an";
        };
    }

    private static BigDecimal montant(long centimes) {
        return BigDecimal.valueOf(centimes, 2);
    }

    private String deviseAffichee() {
        return devise.toUpperCase(Locale.ROOT);
    }
}
