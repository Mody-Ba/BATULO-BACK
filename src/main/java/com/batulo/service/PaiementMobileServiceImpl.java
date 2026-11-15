package com.batulo.service;

import com.batulo.entity.Abonnement;
import com.batulo.entity.Paiement;
import com.batulo.enums.Formule;
import com.batulo.enums.FournisseurPaiement;
import com.batulo.enums.StatutPaiement;
import com.batulo.exception.RegleGestionException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.AbonnementRepository;
import com.batulo.repository.PaiementRepository;
import com.batulo.service.mappers.AbonnementMapper;
import com.batulo.service.model.DemandePaiementMobileModel;
import com.batulo.service.model.PaiementMobileModel;
import com.batulo.service.model.SessionMoosylModel;
import com.batulo.service.model.StatutPaiementMobileModel;
import com.batulo.service.paiement.MoosylClient;
import com.batulo.service.paiement.SignatureWebhookMoosyl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Transactions explicites (TransactionTemplate) : la relecture de la session chez Moosyl se fait hors transaction,
 * sans garder de verrou en base pendant l'appel HTTP ; l'activation reprend ensuite le verrou du paiement.
 */
@Service
public class PaiementMobileServiceImpl implements PaiementMobileService {

    private static final Logger log = LoggerFactory.getLogger(PaiementMobileServiceImpl.class);

    static final String DEVISE = "MRU";
    private static final Set<String> EVENEMENTS_PAIEMENT = Set.of("payment-created", "payment-updated");
    /** Statuts d'un paiement Moosyl : seul « completed » veut dire payé. */
    private static final String MOOSYL_PAYE = "completed";
    private static final Set<String> MOOSYL_ECHEC = Set.of("failed", "cancelled");
    /** Statuts d'une session Moosyl qui ne pourra plus être payée. */
    private static final Set<String> SESSION_TERMINEE_SANS_PAIEMENT = Set.of("expired", "cancelled");

    private final PaiementRepository paiementRepository;
    private final AbonnementRepository abonnementRepository;
    private final AbonnementMapper abonnementMapper;
    private final MoosylClient moosylClient;
    private final ActivationPaiement activationPaiement;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transaction;
    private final TransactionTemplate lecture;

    @Value("${batulo.moosyl.secret-webhook}")
    private String secretWebhook;
    @Value("${batulo.moosyl.url-retour}")
    private String urlRetour;
    @Value("${batulo.moosyl.prix-mensuel-mru}")
    private long prixMensuelMru;
    @Value("${batulo.moosyl.prix-annuel-mru}")
    private long prixAnnuelMru;
    @Value("${batulo.moosyl.expiration-minutes}")
    private int expirationMinutes;
    @Value("${batulo.abonnement.jours-alerte-expiration}")
    private int joursAlerte;

    public PaiementMobileServiceImpl(PaiementRepository paiementRepository, AbonnementRepository abonnementRepository,
                                     AbonnementMapper abonnementMapper, MoosylClient moosylClient,
                                     ActivationPaiement activationPaiement, ObjectMapper objectMapper,
                                     PlatformTransactionManager transactionManager) {
        this.paiementRepository = paiementRepository;
        this.abonnementRepository = abonnementRepository;
        this.abonnementMapper = abonnementMapper;
        this.moosylClient = moosylClient;
        this.activationPaiement = activationPaiement;
        this.objectMapper = objectMapper;
        this.transaction = new TransactionTemplate(transactionManager);
        this.lecture = new TransactionTemplate(transactionManager);
        this.lecture.setReadOnly(true);
    }

    /** Prix d'une formule en MRU entiers (Moosyl n'accepte pas de décimales). */
    public long prixMru(Formule formule) {
        return switch (formule) {
            case MENSUEL -> prixMensuelMru;
            case ANNUEL -> prixAnnuelMru;
        };
    }

    @Override
    public PaiementMobileModel demarrer(Long entrepriseId, DemandePaiementMobileModel demande) {
        return transaction.execute(statut -> {
            Abonnement abonnement = abonnementRepository.findByEntrepriseId(entrepriseId)
                    .orElseThrow(() -> new ResourceNotFoundException("ABONNEMENT_INTROUVABLE",
                            "Aucun abonnement pour l'entreprise " + entrepriseId + "."));
            if (!abonnement.getEntreprise().isActif()) {
                throw new RegleGestionException("ENTREPRISE_DESACTIVEE",
                        "Votre entreprise a été désactivée : le paiement est impossible. Contactez Batulo.");
            }
            Formule formule = demande.formule();
            long montantMru = prixMru(formule);
            String reference = UUID.randomUUID().toString();

            Paiement paiement = new Paiement();
            paiement.setAbonnement(abonnement);
            paiement.setFournisseur(FournisseurPaiement.MOOSYL);
            paiement.setReference(reference);
            paiement.setFormule(formule);
            paiement.setMontant(BigDecimal.valueOf(montantMru).setScale(2));
            paiement.setDevise(DEVISE);
            paiement.setStatut(StatutPaiement.EN_ATTENTE);
            paiement = paiementRepository.save(paiement);

            // Échec chez Moosyl : exception, la transaction est annulée et aucun paiement n'est enregistré.
            SessionMoosylModel session = moosylClient.creerSession(reference, montantMru, demande.telephone(),
                    urlRetour(reference), expirationMinutes);
            paiement.setSessionExterneId(session.id());
            log.info("Paiement mobile {} créé pour l'abonnement {} ({}, {} MRU), session Moosyl {}",
                    reference, abonnement.getId(), formule, montantMru, session.id());
            return new PaiementMobileModel(reference, session.checkoutUrl(), formule, paiement.getMontant(), DEVISE,
                    session.expireLe());
        });
    }

    @Override
    public StatutPaiementMobileModel statut(Long entrepriseId, String reference) {
        Paiement paiement = lecture.execute(s -> paiementDe(entrepriseId, reference));
        if (paiement.getStatut() == StatutPaiement.EN_ATTENTE && paiement.getSessionExterneId() != null) {
            // Relecture de secours, hors transaction : le webhook peut ne pas arriver (tests en local sans tunnel).
            Optional<String> session = moosylClient.statutSession(paiement.getSessionExterneId());
            session.ifPresent(s -> {
                if (MOOSYL_PAYE.equals(s)) {
                    transaction.executeWithoutResult(t -> activerSiCoherent(reference, null, "relecture du statut"));
                } else if (SESSION_TERMINEE_SANS_PAIEMENT.contains(s)) {
                    transaction.executeWithoutResult(t -> marquerEchec(reference, "session " + s));
                }
            });
        }
        return lecture.execute(s -> versStatut(paiementDe(entrepriseId, reference)));
    }

    @Override
    public ResultatWebhook traiterWebhook(byte[] corps, String signature, String idLivraison) {
        if (!SignatureWebhookMoosyl.verifier(corps, signature, secretWebhook)) {
            log.warn("Webhook Moosyl {} refusé : signature absente ou invalide{}", idLivraison,
                    secretWebhook == null || secretWebhook.isEmpty() ? " (MOOSYL_WEBHOOK_SECRET absent)" : "");
            return ResultatWebhook.SIGNATURE_INVALIDE;
        }
        JsonNode evenement;
        try {
            evenement = objectMapper.readTree(corps);
        } catch (IOException e) {
            log.warn("Webhook Moosyl {} : corps JSON illisible", idLivraison);
            return ResultatWebhook.CORPS_INVALIDE;
        }
        if (evenement == null || !evenement.isObject()) {
            return ResultatWebhook.CORPS_INVALIDE;
        }
        String nom = evenement.path("event").asText("");
        if (!EVENEMENTS_PAIEMENT.contains(nom)) {
            log.info("Webhook Moosyl {} : événement {} sans effet", idLivraison, nom);
            return ResultatWebhook.IGNORE;
        }
        JsonNode data = evenement.path("data");
        String reference = data.path("transactionId").asText("");
        String statutMoosyl = data.path("status").asText("").toLowerCase(Locale.ROOT);
        BigDecimal montant = data.path("amount").isNumber() ? data.path("amount").decimalValue() : null;
        log.info("Webhook Moosyl {} : {} pour {} (statut {})", idLivraison, nom, reference, statutMoosyl);

        if (MOOSYL_PAYE.equals(statutMoosyl)) {
            Boolean traite = transaction.execute(t -> activerSiCoherent(reference, montant, "webhook " + idLivraison));
            return Boolean.TRUE.equals(traite) ? ResultatWebhook.TRAITE : ResultatWebhook.IGNORE;
        }
        if (MOOSYL_ECHEC.contains(statutMoosyl)) {
            Boolean traite = transaction.execute(t -> marquerEchec(reference, "webhook " + idLivraison));
            return Boolean.TRUE.equals(traite) ? ResultatWebhook.TRAITE : ResultatWebhook.IGNORE;
        }
        return ResultatWebhook.IGNORE;
    }

    /**
     * Active l'abonnement sous verrou. Un paiement déjà réussi n'est pas recompté ; un montant reçu différent du
     * montant attendu n'active rien (journalisé).
     *
     * @param montantRecu montant annoncé par Moosyl ; null : non disponible (relecture de la session)
     * @return true si le paiement est connu et cohérent (réussi, maintenant ou déjà)
     */
    private boolean activerSiCoherent(String reference, BigDecimal montantRecu, String source) {
        Optional<Paiement> trouve = reference.isEmpty() ? Optional.empty()
                : paiementRepository.verrouillerParReference(reference);
        if (trouve.isEmpty()) {
            log.warn("Paiement Moosyl {} inconnu ({}) : ignoré", reference, source);
            return false;
        }
        Paiement paiement = trouve.get();
        if (montantRecu != null && montantRecu.compareTo(paiement.getMontant()) != 0) {
            log.warn("Paiement Moosyl {} : montant reçu {} au lieu de {} ({}) : abonnement inchangé", reference,
                    montantRecu.toPlainString(), paiement.getMontant().toPlainString(), source);
            return false;
        }
        activationPaiement.activer(paiement, Instant.now(), source);
        return true;
    }

    /** Un paiement réussi le reste ; un échec suivi d'un paiement réussi (nouvel essai) active encore l'abonnement. */
    private boolean marquerEchec(String reference, String source) {
        Optional<Paiement> trouve = reference.isEmpty() ? Optional.empty()
                : paiementRepository.verrouillerParReference(reference);
        if (trouve.isEmpty()) {
            log.warn("Paiement Moosyl {} inconnu ({}) : ignoré", reference, source);
            return false;
        }
        Paiement paiement = trouve.get();
        if (paiement.getStatut() == StatutPaiement.EN_ATTENTE) {
            paiement.setStatut(StatutPaiement.ECHOUE);
            log.info("Paiement Moosyl {} échoué ({})", reference, source);
        }
        return true;
    }

    private Paiement paiementDe(Long entrepriseId, String reference) {
        return paiementRepository.trouverMoosylDeLEntreprise(reference, entrepriseId)
                .orElseThrow(() -> new ResourceNotFoundException("PAIEMENT_INTROUVABLE",
                        "Aucun paiement mobile de votre entreprise ne correspond à cette référence."));
    }

    private StatutPaiementMobileModel versStatut(Paiement paiement) {
        Abonnement abonnement = paiement.getAbonnement();
        Instant maintenant = Instant.now();
        return new StatutPaiementMobileModel(paiement.getReference(), paiement.getStatut(), paiement.getFormule(),
                paiement.getMontant(), paiement.getDevise(), paiement.getDateCreation(), paiement.getDatePaiement(),
                abonnementMapper.toModel(abonnement, RegleAbonnement.statutEffectif(abonnement, maintenant, joursAlerte),
                        RegleAbonnement.joursRestants(abonnement, maintenant)));
    }

    private String urlRetour(String reference) {
        return urlRetour + (urlRetour.contains("?") ? "&" : "?") + "reference="
                + URLEncoder.encode(reference, StandardCharsets.UTF_8);
    }
}
