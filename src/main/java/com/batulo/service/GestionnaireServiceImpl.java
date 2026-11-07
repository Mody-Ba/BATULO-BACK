package com.batulo.service;

import com.batulo.entity.Abonnement;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import com.batulo.enums.StatutAbonnement;
import com.batulo.exception.ActionInterditeException;
import com.batulo.exception.CompteExistantException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.AbonnementRepository;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.email.EmailService;
import com.batulo.service.mappers.UtilisateurMapper;
import com.batulo.service.model.CreationGestionnaireModel;
import com.batulo.service.model.ModificationGestionnaireModel;
import com.batulo.service.model.UtilisateurModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class GestionnaireServiceImpl implements GestionnaireService {

    private static final Logger log = LoggerFactory.getLogger(GestionnaireServiceImpl.class);

    /** Ce que l'admin veut faire : la lecture reste permise sur un abonnement expiré. */
    private enum Acces { LECTURE, ECRITURE }

    private final UtilisateurRepository utilisateurRepository;
    private final AbonnementRepository abonnementRepository;
    private final UtilisateurMapper utilisateurMapper;
    private final MotDePasseTemporaire motDePasseTemporaire;
    private final EmailService emailService;

    @Value("${batulo.abonnement.jours-alerte-expiration}")
    private int joursAlerte;

    public GestionnaireServiceImpl(UtilisateurRepository utilisateurRepository,
                                   AbonnementRepository abonnementRepository,
                                   UtilisateurMapper utilisateurMapper,
                                   MotDePasseTemporaire motDePasseTemporaire,
                                   EmailService emailService) {
        this.utilisateurRepository = utilisateurRepository;
        this.abonnementRepository = abonnementRepository;
        this.utilisateurMapper = utilisateurMapper;
        this.motDePasseTemporaire = motDePasseTemporaire;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public UtilisateurModel creer(Long adminId, CreationGestionnaireModel creation) {
        Utilisateur admin = adminAutorise(adminId, Acces.ECRITURE);
        String courriel = NormaliseurCourriel.normaliser(creation.courriel());
        if (utilisateurRepository.existsByCourriel(courriel)) {
            throw new CompteExistantException();
        }
        Utilisateur gestionnaire = new Utilisateur();
        gestionnaire.setPrenom(creation.prenom().trim());
        gestionnaire.setNom(creation.nom().trim());
        gestionnaire.setCourriel(courriel);
        gestionnaire.setRole(Role.GESTIONNAIRE);
        gestionnaire.setEntreprise(admin.getEntreprise());
        gestionnaire.setActif(true);
        // AD-09 : la création de sondages est une permission accordée explicitement par l'admin.
        gestionnaire.setPeutCreerSondages(false);
        String motDePasse = motDePasseTemporaire.attribuer(gestionnaire);
        gestionnaire = utilisateurRepository.save(gestionnaire);

        emailService.envoyerMotDePasseTemporaire(courriel, gestionnaire.getPrenom(), motDePasse,
                gestionnaire.getMotDePasseTemporaireExpireLe());
        log.info("Admin {} : gestionnaire {} créé dans l'entreprise {}",
                adminId, gestionnaire.getId(), admin.getEntreprise().getId());
        return utilisateurMapper.toModel(gestionnaire);
    }

    @Override
    public Page<UtilisateurModel> lister(Long adminId, int page, int taille) {
        Utilisateur admin = adminAutorise(adminId, Acces.LECTURE);
        return utilisateurRepository.findByEntrepriseIdAndRole(admin.getEntreprise().getId(), Role.GESTIONNAIRE,
                        PageRequest.of(page, taille, Sort.by(Sort.Direction.DESC, "dateCreation", "id")))
                .map(utilisateurMapper::toModel);
    }

    @Override
    public UtilisateurModel detail(Long adminId, Long gestionnaireId) {
        return utilisateurMapper.toModel(gestionnaireDe(adminAutorise(adminId, Acces.LECTURE), gestionnaireId));
    }

    @Override
    @Transactional
    public UtilisateurModel modifier(Long adminId, Long gestionnaireId, ModificationGestionnaireModel modification) {
        Utilisateur gestionnaire = gestionnaireDe(adminAutorise(adminId, Acces.ECRITURE), gestionnaireId);
        String courriel = NormaliseurCourriel.normaliser(modification.courriel());
        if (utilisateurRepository.existsByCourrielAndIdNot(courriel, gestionnaireId)) {
            throw new CompteExistantException();
        }
        gestionnaire.setPrenom(modification.prenom().trim());
        gestionnaire.setNom(modification.nom().trim());
        gestionnaire.setCourriel(courriel);
        log.info("Admin {} : gestionnaire {} modifié", adminId, gestionnaireId);
        return utilisateurMapper.toModel(gestionnaire);
    }

    @Override
    @Transactional
    public void supprimer(Long adminId, Long gestionnaireId) {
        Utilisateur gestionnaire = gestionnaireDe(adminAutorise(adminId, Acces.ECRITURE), gestionnaireId);
        // Les jetons et notifications du compte sont supprimés par les clés étrangères ON DELETE CASCADE.
        utilisateurRepository.delete(gestionnaire);
        log.info("Admin {} : gestionnaire {} supprimé", adminId, gestionnaireId);
    }

    @Override
    @Transactional
    public UtilisateurModel changerActivation(Long adminId, Long gestionnaireId, boolean actif) {
        Utilisateur gestionnaire = gestionnaireDe(adminAutorise(adminId, Acces.ECRITURE), gestionnaireId);
        gestionnaire.setActif(actif);
        log.info("Admin {} : gestionnaire {} {}", adminId, gestionnaireId, actif ? "activé" : "désactivé");
        return utilisateurMapper.toModel(gestionnaire);
    }

    @Override
    @Transactional
    public UtilisateurModel changerPermissionSondages(Long adminId, Long gestionnaireId, boolean peutCreerSondages) {
        Utilisateur gestionnaire = gestionnaireDe(adminAutorise(adminId, Acces.ECRITURE), gestionnaireId);
        gestionnaire.setPeutCreerSondages(peutCreerSondages);
        log.info("Admin {} : création de sondages {} au gestionnaire {}",
                adminId, peutCreerSondages ? "autorisée" : "retirée", gestionnaireId);
        return utilisateurMapper.toModel(gestionnaire);
    }

    @Override
    @Transactional
    public UtilisateurModel renvoyerMotDePasseTemporaire(Long adminId, Long gestionnaireId) {
        Utilisateur gestionnaire = gestionnaireDe(adminAutorise(adminId, Acces.ECRITURE), gestionnaireId);
        String motDePasse = motDePasseTemporaire.attribuer(gestionnaire);
        emailService.envoyerMotDePasseTemporaire(gestionnaire.getCourriel(), gestionnaire.getPrenom(), motDePasse,
                gestionnaire.getMotDePasseTemporaireExpireLe());
        log.info("Admin {} : nouveau mot de passe temporaire pour le gestionnaire {}", adminId, gestionnaireId);
        return utilisateurMapper.toModel(gestionnaire);
    }

    /**
     * Charge l'admin et vérifie que l'abonnement de son entreprise permet l'accès demandé.
     * TEMPORAIRE - étape 7 : l'admin viendra du jeton JWT ; la sécurité appliquera aussi ces règles.
     */
    private Utilisateur adminAutorise(Long adminId, Acces acces) {
        Utilisateur admin = utilisateurRepository.findByIdAndRole(adminId, Role.ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException("ADMIN_INTROUVABLE",
                        "Aucun admin avec l'identifiant " + adminId + "."));
        Abonnement abonnement = abonnementRepository.findByEntrepriseId(admin.getEntreprise().getId())
                .orElseThrow(() -> new ActionInterditeException("ABONNEMENT_EN_ATTENTE_PAIEMENT",
                        "Votre entreprise n'a pas d'abonnement : payez une formule pour gérer vos gestionnaires."));
        StatutAbonnement statut = RegleAbonnement.statutEffectif(abonnement, Instant.now(), joursAlerte);
        switch (statut) {
            case EN_ATTENTE_PAIEMENT -> throw new ActionInterditeException("ABONNEMENT_EN_ATTENTE_PAIEMENT",
                    "Votre abonnement est en attente de paiement : payez une formule pour gérer vos gestionnaires.");
            case DESACTIVE -> throw new ActionInterditeException("ABONNEMENT_DESACTIVE",
                    "Votre compte a été désactivé par Batulo : aucune action n'est possible.");
            case EXPIRE -> {
                if (acces == Acces.ECRITURE) {
                    throw new ActionInterditeException("ABONNEMENT_EXPIRE",
                            "Votre abonnement est expiré : accès en lecture seule. Renouvelez-le pour modifier "
                                    + "vos gestionnaires.");
                }
            }
            case ACTIF, EXPIRE_BIENTOT -> {
                // Accès complet.
            }
        }
        return admin;
    }

    /** Un gestionnaire de l'entreprise de l'admin ; sinon 404, sans révéler s'il existe ailleurs. */
    private Utilisateur gestionnaireDe(Utilisateur admin, Long gestionnaireId) {
        return utilisateurRepository.findByIdAndEntrepriseIdAndRole(gestionnaireId, admin.getEntreprise().getId(),
                        Role.GESTIONNAIRE)
                .orElseThrow(() -> new ResourceNotFoundException("GESTIONNAIRE_INTROUVABLE",
                        "Aucun gestionnaire avec l'identifiant " + gestionnaireId + " dans votre entreprise."));
    }
}
