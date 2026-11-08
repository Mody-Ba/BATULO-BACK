package com.batulo.service;

import com.batulo.entity.Sondage;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.AssignationRepository;
import com.batulo.repository.SondageRepository;
import com.batulo.repository.UtilisateurRepository;
import org.springframework.stereotype.Component;

/**
 * Règle unique de visibilité des sondages, partagée par les services (sondages, collecte) :
 * l'admin voit tous les sondages de son entreprise ; le gestionnaire voit une lignée s'il a créé l'une de ses
 * versions ou si elle lui est assignée (GE-04, RG-02). Sinon 404, sans révéler que le sondage existe.
 */
@Component
public class AccesSondage {

    private final UtilisateurRepository utilisateurRepository;
    private final SondageRepository sondageRepository;
    private final AssignationRepository assignationRepository;

    public AccesSondage(UtilisateurRepository utilisateurRepository, SondageRepository sondageRepository,
                        AssignationRepository assignationRepository) {
        this.utilisateurRepository = utilisateurRepository;
        this.sondageRepository = sondageRepository;
        this.assignationRepository = assignationRepository;
    }

    /** L'admin ou le gestionnaire du jeton, avec son entreprise. */
    public Utilisateur acteur(Long utilisateurId) {
        return utilisateurRepository.findWithEntrepriseById(utilisateurId)
                .filter(u -> u.getEntreprise() != null)
                .orElseThrow(() -> new ResourceNotFoundException("UTILISATEUR_INTROUVABLE", "Compte introuvable."));
    }

    public Sondage sondageLisible(Utilisateur acteur, Long sondageId) {
        return sondageRepository.findByIdAndEntrepriseId(sondageId, acteur.getEntreprise().getId())
                .filter(s -> peutLire(acteur, s))
                .orElseThrow(() -> new ResourceNotFoundException("SONDAGE_INTROUVABLE",
                        "Aucun sondage " + sondageId + " accessible."));
    }

    public boolean peutLire(Utilisateur acteur, Sondage sondage) {
        if (acteur.getRole() == Role.ADMIN) {
            return true;
        }
        Long lignee = sondage.getIdLignee();
        return sondageRepository.aCreeDansLaLignee(lignee, acteur.getId())
                || assignationRepository.existsBySondageIdAndGestionnaireId(lignee, acteur.getId());
    }
}
