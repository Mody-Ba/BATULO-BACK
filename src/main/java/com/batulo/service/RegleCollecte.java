package com.batulo.service;

import com.batulo.entity.Entreprise;
import com.batulo.entity.Sondage;
import com.batulo.enums.StatutAbonnement;
import com.batulo.enums.StatutSondage;
import com.batulo.repository.AbonnementRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

/**
 * Quand une version accepte-t-elle des réponses (RE-03, cahier §5) ? Il faut que le sondage soit EN_COURS, que
 * l'entreprise soit active et que son abonnement soit ACTIF ou EXPIRE_BIENTOT : un abonnement expiré, désactivé ou
 * en attente ferme la collecte.
 */
@Component
public class RegleCollecte {

    private final AbonnementRepository abonnementRepository;
    private final FormatDate formatDate;

    @Value("${batulo.abonnement.jours-alerte-expiration}")
    private int joursAlerte;

    public RegleCollecte(AbonnementRepository abonnementRepository, FormatDate formatDate) {
        this.abonnementRepository = abonnementRepository;
        this.formatDate = formatDate;
    }

    /** L'entreprise peut-elle collecter des réponses (entreprise active, abonnement actif) ? */
    public boolean entrepriseAutorisee(Entreprise entreprise, Instant maintenant) {
        if (!entreprise.isActif()) {
            return false;
        }
        StatutAbonnement statut = abonnementRepository.findByEntrepriseId(entreprise.getId())
                .map(a -> RegleAbonnement.statutEffectif(a, maintenant, joursAlerte))
                .orElse(StatutAbonnement.EN_ATTENTE_PAIEMENT);
        return statut == StatutAbonnement.ACTIF || statut == StatutAbonnement.EXPIRE_BIENTOT;
    }

    /** Raison pour laquelle cette version n'accepte pas de réponses ; vide si elle en accepte. */
    public Optional<String> raisonFermeture(Sondage version, Instant maintenant) {
        if (!entrepriseAutorisee(version.getEntreprise(), maintenant)) {
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
