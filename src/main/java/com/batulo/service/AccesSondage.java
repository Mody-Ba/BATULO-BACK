package com.batulo.service;

import com.batulo.entity.Sondage;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import com.batulo.exception.ActionInterditeException;
import com.batulo.exception.ConflitException;
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

    /**
     * Droit de gérer le sondage (modifier, publier, fermer, versionner, supprimer...), quel que soit son statut :
     * l'admin, ou le gestionnaire qui a créé CETTE version, avec la permission de créer des sondages. Un gestionnaire
     * qui le voit seulement par assignation (ou par sa lignée) ne le gère pas : 403 ACTION_RESERVEE_CREATEUR.
     */
    public Sondage sondageGere(Long utilisateurId, Long sondageId) {
        Utilisateur acteur = acteur(utilisateurId);
        verifierDroitDeModifier(acteur);
        Sondage sondage = sondageLisible(acteur, sondageId);
        if (acteur.getRole() == Role.GESTIONNAIRE
                && (sondage.getCreePar() == null || !sondage.getCreePar().getId().equals(acteur.getId()))) {
            throw new ActionInterditeException("ACTION_RESERVEE_CREATEUR",
                    "Vous pouvez consulter ce sondage, mais seuls son créateur et l'admin peuvent le modifier, "
                            + "le publier ou le fermer.");
        }
        return sondage;
    }

    /** Droit de gérer ET contenu modifiable : seul un brouillon l'est, un sondage publié est gelé. */
    public Sondage sondageModifiable(Long utilisateurId, Long sondageId) {
        Sondage sondage = sondageGere(utilisateurId, sondageId);
        if (sondage.isPublie()) {
            throw new ConflitException("SONDAGE_PUBLIE", "Ce sondage est publié : son contenu est gelé. "
                    + "Créez une nouvelle version (POST /api/sondages/" + sondageId + "/nouvelle-version) pour le modifier.");
        }
        return sondage;
    }

    /** Un gestionnaire sans la permission garde la lecture de ses sondages, mais ne crée ni ne modifie plus. */
    public static void verifierDroitDeModifier(Utilisateur acteur) {
        if (acteur.getRole() == Role.GESTIONNAIRE && !acteur.isPeutCreerSondages()) {
            throw new ActionInterditeException("PERMISSION_SONDAGES_REQUISE",
                    "Votre admin ne vous a pas donné la permission de créer ou modifier des sondages.");
        }
    }
}
