package com.batulo.service;

import com.batulo.entity.CreditSondage;
import com.batulo.entity.Entreprise;
import com.batulo.entity.Sondage;
import com.batulo.exception.CreditsEpuisesException;
import com.batulo.repository.CreditSondageRepository;
import com.batulo.repository.SondageRepository;
import com.batulo.service.model.CreditsModel;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Seul point qui modifie le solde de sondages achetés. Crédit : par ActivationPaiement (sous verrou du paiement,
 * donc une seule fois par paiement). Débit : à la publication, dans la transaction de la publication, par des
 * mises à jour SQL atomiques (jamais lire puis réécrire le solde).
 */
@Component
public class CreditsSondages {

    private static final Logger log = LoggerFactory.getLogger(CreditsSondages.class);

    private final CreditSondageRepository creditRepository;
    private final SondageRepository sondageRepository;
    private final RegleCollecte regleCollecte;
    private final EntityManager entityManager;

    public CreditsSondages(CreditSondageRepository creditRepository, SondageRepository sondageRepository,
                           RegleCollecte regleCollecte, EntityManager entityManager) {
        this.creditRepository = creditRepository;
        this.sondageRepository = sondageRepository;
        this.regleCollecte = regleCollecte;
        this.entityManager = entityManager;
    }

    /** Solde et totaux, relus en base ; zéros si l'entreprise n'a jamais acheté. */
    public CreditsModel de(Long entrepriseId) {
        return creditRepository.infos(entrepriseId)
                .map(i -> new CreditsModel(i.solde(), i.totalAchete(), i.totalUtilise()))
                .orElse(CreditsModel.AUCUN);
    }

    /** L'entreprise a déjà acheté des sondages (accès complet sans abonnement actif). */
    public boolean compteCredits(Long entrepriseId) {
        return creditRepository.existsByEntrepriseIdAndTotalAcheteGreaterThan(entrepriseId, 0);
    }

    /**
     * Ajoute un achat au solde. La ligne est créée au premier achat, sous verrou de l'entreprise (deux premiers achats
     * simultanés ne la créent qu'une fois). À appeler dans la transaction qui marque le paiement REUSSI.
     *
     * @return le solde à jour
     */
    public CreditsModel crediter(Entreprise entreprise, int quantite, Instant maintenant) {
        Long id = entreprise.getId();
        if (creditRepository.crediter(id, quantite, maintenant) == 0) {
            entityManager.lock(entreprise, LockModeType.PESSIMISTIC_WRITE);
            if (creditRepository.crediter(id, quantite, maintenant) == 0) {
                creditRepository.saveAndFlush(new CreditSondage(id, quantite, quantite));
            }
        }
        CreditsModel credits = de(id);
        log.info("Entreprise {} : {} sondage(s) crédité(s), solde {}", id, quantite, credits.solde());
        return credits;
    }

    /**
     * Règle de publication : abonnement actif -> rien ; lignée déjà payée par crédit -> rien ; sinon un crédit est
     * utilisé (une seule fois par lignée, quelle que soit la version publiée) et la lignée devient « payée par
     * crédit ». À appeler dans la transaction de la publication : un refus l'annule entièrement.
     *
     * @return true si un crédit vient d'être utilisé
     * @throws CreditsEpuisesException 402 si aucun crédit n'est disponible
     */
    public boolean utiliserPourPublication(Sondage version, Instant maintenant) {
        Sondage racine = version.getRacine();
        Entreprise entreprise = racine.getEntreprise();
        if (racine.getCreditUtiliseLe() != null || regleCollecte.abonnementActif(entreprise, maintenant)) {
            return false;
        }
        if (sondageRepository.marquerCreditUtilise(racine.getId(), maintenant) == 0) {
            // Une publication simultanée de la même lignée vient de la payer : on reprend sa marque, sans débit.
            // (L'entité en mémoire doit porter la valeur en base, sinon son écriture l'effacerait.)
            racine.setCreditUtiliseLe(sondageRepository.creditUtiliseLe(racine.getId()));
            return false;
        }
        racine.setCreditUtiliseLe(maintenant);
        if (creditRepository.debiter(entreprise.getId(), maintenant) == 0) {
            throw new CreditsEpuisesException("Vous n'avez plus de sondages disponibles : achetez des sondages ou "
                    + "abonnez-vous pour publier ce sondage.");
        }
        log.info("Entreprise {} : un crédit utilisé pour la lignée {} (version {})", entreprise.getId(),
                racine.getId(), version.getVersion());
        return true;
    }
}
