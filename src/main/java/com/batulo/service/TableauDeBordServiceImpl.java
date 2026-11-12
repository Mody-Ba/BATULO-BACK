package com.batulo.service;

import com.batulo.entity.Sondage;
import com.batulo.enums.Role;
import com.batulo.enums.StatutSondage;
import com.batulo.repository.ReponseRepository;
import com.batulo.repository.SondageRepository;
import com.batulo.repository.SondageSpecifications;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.model.TableauDeBordModel;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class TableauDeBordServiceImpl implements TableauDeBordService {

    private final AbonnementService abonnementService;
    private final UtilisateurRepository utilisateurRepository;
    private final SondageRepository sondageRepository;
    private final ReponseRepository reponseRepository;

    public TableauDeBordServiceImpl(AbonnementService abonnementService, UtilisateurRepository utilisateurRepository,
                                    SondageRepository sondageRepository, ReponseRepository reponseRepository) {
        this.abonnementService = abonnementService;
        this.utilisateurRepository = utilisateurRepository;
        this.sondageRepository = sondageRepository;
        this.reponseRepository = reponseRepository;
    }

    @Override
    public TableauDeBordModel tableauDeBord(Long entrepriseId) {
        Instant maintenant = Instant.now();
        TableauDeBordModel.Abonnement abonnement = abonnementService.trouverAbonnement(entrepriseId)
                .map(a -> new TableauDeBordModel.Abonnement(a.formule(), a.statut(), a.dateExpiration(),
                        a.joursRestants()))
                .orElse(null);

        long actifs = 0;
        long desactives = 0;
        for (Object[] ligne : utilisateurRepository.compterParActivation(entrepriseId, Role.GESTIONNAIRE)) {
            if ((Boolean) ligne[0]) {
                actifs = (Long) ligne[1];
            } else {
                desactives = (Long) ligne[1];
            }
        }

        TableauDeBordModel.Sondages sondages = new TableauDeBordModel.Sondages(
                compterSondages(entrepriseId, StatutSondage.NON_COMMENCE, maintenant),
                compterSondages(entrepriseId, StatutSondage.EN_COURS, maintenant),
                compterSondages(entrepriseId, StatutSondage.TERMINE, maintenant));

        return new TableauDeBordModel(abonnement, new TableauDeBordModel.Gestionnaires(actifs, desactives), sondages,
                reponseRepository.compterPourEntreprise(entrepriseId));
    }

    /** Une lignée = un sondage, compté au statut de sa ligne représentante (comme ?derniereVersion=true). */
    private long compterSondages(Long entrepriseId, StatutSondage statut, Instant maintenant) {
        Specification<Sondage> filtre = SondageSpecifications.deLEntreprise(entrepriseId)
                .and(SondageSpecifications.parStatut(statut, maintenant))
                .and(SondageSpecifications.representantDeLignee(maintenant));
        return sondageRepository.count(filtre);
    }
}
