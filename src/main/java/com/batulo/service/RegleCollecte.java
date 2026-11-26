package com.batulo.service;

import com.batulo.entity.Entreprise;
import com.batulo.entity.Sondage;
import com.batulo.enums.StatutAbonnement;
import com.batulo.enums.StatutSondage;
import com.batulo.repository.AbonnementRepository;
import com.batulo.repository.CreditSondageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Quand une version accepte-t-elle des réponses (RE-03, cahier §5) ? Il faut que le sondage soit EN_COURS, que
 * l'entreprise soit active et que sa lignée soit couverte : abonnement ACTIF ou EXPIRE_BIENTOT, ou lignée publiée
 * avec un crédit (achat de sondages, pour toujours, même solde à 0). Une lignée publiée pendant un abonnement se
 * ferme quand il expire, même si l'entreprise a des crédits.
 */
@Component
public class RegleCollecte {

    private final AbonnementRepository abonnementRepository;
    private final CreditSondageRepository creditRepository;
    private final FormatDate formatDate;

    @Value("${batulo.abonnement.jours-alerte-expiration}")
    private int joursAlerte;

    public RegleCollecte(AbonnementRepository abonnementRepository, CreditSondageRepository creditRepository,
                         FormatDate formatDate) {
        this.abonnementRepository = abonnementRepository;
        this.creditRepository = creditRepository;
        this.formatDate = formatDate;
    }

    /** Abonnement ACTIF ou EXPIRE_BIENTOT (entreprise active) : publication illimitée, crédits jamais touchés. */
    public boolean abonnementActif(Entreprise entreprise, Instant maintenant) {
        if (!entreprise.isActif()) {
            return false;
        }
        StatutAbonnement statut = abonnementRepository.findByEntrepriseId(entreprise.getId())
                .map(a -> RegleAbonnement.statutEffectif(a, maintenant, joursAlerte))
                .orElse(StatutAbonnement.EN_ATTENTE_PAIEMENT);
        return statut == StatutAbonnement.ACTIF || statut == StatutAbonnement.EXPIRE_BIENTOT;
    }

    /**
     * L'entreprise peut-elle travailler normalement (entreprise active, et abonnement actif ou compte de crédits) ?
     * Sert à la conversation admin <-> gestionnaire.
     */
    public boolean entrepriseAutorisee(Entreprise entreprise, Instant maintenant) {
        return abonnementActif(entreprise, maintenant) || (entreprise.isActif()
                && creditRepository.existsByEntrepriseIdAndTotalAcheteGreaterThan(entreprise.getId(), 0));
    }

    /** La lignée peut-elle collecter (et envoyer ses invitations) ? {@code racine} : sa version 1. */
    public boolean ligneeAutorisee(Sondage racine, Instant maintenant) {
        Entreprise entreprise = racine.getEntreprise();
        return entreprise.isActif()
                && (racine.getCreditUtiliseLe() != null || abonnementActif(entreprise, maintenant));
    }

    /** Raison pour laquelle cette version n'accepte pas de réponses ; vide si elle en accepte. */
    public Optional<String> raisonFermeture(Sondage version, Instant maintenant) {
        if (!ligneeAutorisee(version.getRacine(), maintenant)) {
            return Optional.of("L'abonnement de l'entreprise n'est pas actif : la collecte est fermée.");
        }
        StatutSondage statut = RegleSondage.statut(version, maintenant);
        return switch (statut) {
            case EN_COURS -> Optional.empty();
            case NON_COMMENCE -> Optional.of(version.isPublie()
                    ? "Ouverture prévue le " + formatDate.jour(version.getDatePublication()) + "."
                    : "Brouillon : le sondage n'est pas encore publié.");
            case TERMINE -> Optional.of("Le sondage est terminé.");
        };
    }
}
