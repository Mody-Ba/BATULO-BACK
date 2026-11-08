package com.batulo.service;

import com.batulo.entity.Assignation;
import com.batulo.entity.Sondage;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.RelationSondage;
import com.batulo.enums.Role;
import com.batulo.enums.TypeNotification;
import com.batulo.exception.ConflitException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.AssignationRepository;
import com.batulo.repository.SondageRepository;
import com.batulo.repository.SondageSpecifications;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.email.EmailService;
import com.batulo.service.mappers.AssignationMapper;
import com.batulo.service.model.AssignationModel;
import com.batulo.service.model.SondageGestionnaireModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AssignationServiceImpl implements AssignationService {

    private static final Logger log = LoggerFactory.getLogger(AssignationServiceImpl.class);

    private final AssignationRepository assignationRepository;
    private final SondageRepository sondageRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AssignationMapper assignationMapper;
    private final NotificationService notificationService;
    private final EmailService emailService;

    public AssignationServiceImpl(AssignationRepository assignationRepository,
                                  SondageRepository sondageRepository,
                                  UtilisateurRepository utilisateurRepository,
                                  AssignationMapper assignationMapper,
                                  NotificationService notificationService,
                                  EmailService emailService) {
        this.assignationRepository = assignationRepository;
        this.sondageRepository = sondageRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.assignationMapper = assignationMapper;
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public List<AssignationModel> assigner(Long adminId, Long sondageId, List<Long> gestionnaireIds) {
        Utilisateur admin = admin(adminId);
        Sondage lignee = lignee(admin, sondageId);

        // Tout est vérifié avant d'écrire : une erreur sur un gestionnaire n'assigne personne.
        List<Utilisateur> nouveaux = new ArrayList<>();
        for (Long gestionnaireId : new LinkedHashSet<>(gestionnaireIds)) {
            Utilisateur gestionnaire = gestionnaire(admin, gestionnaireId);
            if (!gestionnaire.isActif()) {
                throw new ConflitException("GESTIONNAIRE_DESACTIVE", "Le gestionnaire " + gestionnaire.getPrenom() + " "
                        + gestionnaire.getNom() + " est désactivé : réactivez-le avant de lui assigner un sondage.");
            }
            if (sondageRepository.aCreeDansLaLignee(lignee.getId(), gestionnaireId)) {
                throw new ConflitException("GESTIONNAIRE_CREATEUR", gestionnaire.getPrenom() + " " + gestionnaire.getNom()
                        + " a créé ce sondage : il le voit déjà.");
            }
            if (!assignationRepository.existsBySondageIdAndGestionnaireId(lignee.getId(), gestionnaireId)) {
                nouveaux.add(gestionnaire);
            }
        }

        String nomAdmin = admin.getPrenom() + " " + admin.getNom();
        for (Utilisateur gestionnaire : nouveaux) {
            Assignation assignation = new Assignation();
            assignation.setSondage(lignee);
            assignation.setGestionnaire(gestionnaire);
            assignation.setAssignePar(admin);
            assignationRepository.save(assignation);

            notificationService.notifier(gestionnaire.getId(), TypeNotification.SONDAGE_ASSIGNE, "Sondage assigné",
                    nomAdmin + " vous a assigné le sondage « " + lignee.getTitre() + " ».");
            emailService.envoyerAssignationSondage(gestionnaire.getCourriel(), gestionnaire.getPrenom(),
                    lignee.getTitre(), nomAdmin);
            log.info("Sondage {} assigné au gestionnaire {} par l'admin {}",
                    lignee.getId(), gestionnaire.getId(), adminId);
        }
        return assignations(lignee);
    }

    @Override
    @Transactional
    public void retirer(Long adminId, Long sondageId, Long gestionnaireId) {
        Utilisateur admin = admin(adminId);
        Sondage lignee = lignee(admin, sondageId);
        Assignation assignation = assignationRepository.findBySondageIdAndGestionnaireId(lignee.getId(), gestionnaireId)
                .orElseThrow(() -> new ResourceNotFoundException("ASSIGNATION_INTROUVABLE",
                        "Ce sondage n'est pas assigné au gestionnaire " + gestionnaireId + "."));
        assignationRepository.delete(assignation);
        notificationService.notifier(gestionnaireId, TypeNotification.SONDAGE_DESASSIGNE, "Assignation retirée",
                "Le sondage « " + lignee.getTitre() + " » ne vous est plus assigné.");
        log.info("Assignation du sondage {} au gestionnaire {} retirée par l'admin {}",
                lignee.getId(), gestionnaireId, adminId);
    }

    @Override
    public List<AssignationModel> gestionnairesDuSondage(Long adminId, Long sondageId) {
        return assignations(lignee(admin(adminId), sondageId));
    }

    @Override
    public Page<SondageGestionnaireModel> sondagesDuGestionnaire(Long adminId, Long gestionnaireId, int page,
                                                                 int taille) {
        Utilisateur admin = admin(adminId);
        Utilisateur gestionnaire = gestionnaire(admin, gestionnaireId);
        Page<Sondage> sondages = sondageRepository.findAll(
                SondageSpecifications.deLEntreprise(admin.getEntreprise().getId())
                        .and(SondageSpecifications.visiblesParGestionnaire(gestionnaire.getId())),
                PageRequest.of(page, taille, Sort.by(Sort.Direction.DESC, "dateModification", "id")));
        Set<Long> assignees = new HashSet<>(assignationRepository.ligneesAssignees(gestionnaire.getId()));
        Instant maintenant = Instant.now();
        return sondages.map(s -> {
            boolean createur = s.getCreePar() != null && s.getCreePar().getId().equals(gestionnaire.getId());
            RelationSondage relation = !createur && assignees.contains(s.getIdLignee())
                    ? RelationSondage.ASSIGNE : RelationSondage.CREATEUR;
            return assignationMapper.toModel(s, relation, maintenant);
        });
    }

    private List<AssignationModel> assignations(Sondage lignee) {
        return assignationRepository.findBySondageIdOrderByDateAssignationAscIdAsc(lignee.getId()).stream()
                .map(assignationMapper::toModel)
                .toList();
    }

    private Utilisateur admin(Long adminId) {
        return utilisateurRepository.findByIdAndRole(adminId, Role.ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException("ADMIN_INTROUVABLE",
                        "Aucun admin avec l'identifiant " + adminId + "."));
    }

    /** Version 1 de la lignée du sondage, s'il appartient à l'entreprise de l'admin. */
    private Sondage lignee(Utilisateur admin, Long sondageId) {
        Sondage sondage = sondageRepository.findByIdAndEntrepriseId(sondageId, admin.getEntreprise().getId())
                .orElseThrow(() -> new ResourceNotFoundException("SONDAGE_INTROUVABLE",
                        "Aucun sondage " + sondageId + " accessible."));
        return sondage.getLignee() == null ? sondage : sondage.getLignee();
    }

    private Utilisateur gestionnaire(Utilisateur admin, Long gestionnaireId) {
        return utilisateurRepository.findByIdAndEntrepriseIdAndRole(gestionnaireId, admin.getEntreprise().getId(),
                        Role.GESTIONNAIRE)
                .orElseThrow(() -> new ResourceNotFoundException("GESTIONNAIRE_INTROUVABLE",
                        "Aucun gestionnaire avec l'identifiant " + gestionnaireId + " dans votre entreprise."));
    }
}
