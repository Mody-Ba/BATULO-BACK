package com.batulo.service;

import com.batulo.entity.Abonnement;
import com.batulo.entity.DemandeAbonnement;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.CoteConversation;
import com.batulo.enums.Formule;
import com.batulo.enums.Role;
import com.batulo.enums.StatutDemande;
import com.batulo.enums.TypeMessage;
import com.batulo.enums.TypeNotification;
import com.batulo.exception.ConflitException;
import com.batulo.exception.RegleGestionException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.AbonnementRepository;
import com.batulo.repository.DemandeAbonnementRepository;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.model.DemandeAbonnementModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class DemandeAbonnementServiceImpl implements DemandeAbonnementService {

    private static final Logger log = LoggerFactory.getLogger(DemandeAbonnementServiceImpl.class);

    private final DemandeAbonnementRepository demandeRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final AbonnementRepository abonnementRepository;
    private final Messagerie messagerie;
    private final NotificationService notificationService;

    @Value("${batulo.abonnement.jours-alerte-expiration}")
    private int joursAlerte;

    public DemandeAbonnementServiceImpl(DemandeAbonnementRepository demandeRepository,
                                        UtilisateurRepository utilisateurRepository,
                                        AbonnementRepository abonnementRepository, Messagerie messagerie,
                                        NotificationService notificationService) {
        this.demandeRepository = demandeRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.abonnementRepository = abonnementRepository;
        this.messagerie = messagerie;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public DemandeAbonnementModel demander(Long adminId, Formule formuleSouhaitee, String message) {
        Utilisateur admin = utilisateurRepository.findByIdAndRole(adminId, Role.ADMIN)
                .orElseThrow(() -> new ResourceNotFoundException("ADMIN_INTROUVABLE", "Compte introuvable."));
        if (demandeRepository.existsByEntrepriseIdAndStatut(admin.getEntreprise().getId(), StatutDemande.EN_ATTENTE)) {
            throw new ConflitException("DEMANDE_EN_COURS",
                    "Une demande attend déjà la réponse de Batulo : vous pouvez lui écrire dans la messagerie.");
        }
        DemandeAbonnement demande = new DemandeAbonnement();
        demande.setEntreprise(admin.getEntreprise());
        demande.setAdmin(admin);
        demande.setFormuleSouhaitee(formuleSouhaitee);
        demande.setMessage(message.trim());
        demande.setStatut(StatutDemande.EN_ATTENTE);
        demande = demandeRepository.save(demande);

        messagerie.poster(messagerie.conversation(admin, null), admin, CoteConversation.ADMIN,
                TypeMessage.DEMANDE_ABONNEMENT, demande.getId(), "Demande d'augmentation d'abonnement : formule "
                        + libelle(formuleSouhaitee) + " souhaitée.\n" + demande.getMessage(), false);
        notificationService.notifierSuperAdmins(TypeNotification.DEMANDE_ABONNEMENT, "Demande d'abonnement",
                admin.getEntreprise().getNom() + " demande la formule " + libelle(formuleSouhaitee) + ".");
        log.info("Demande d'abonnement {} de l'admin {} (formule {})", demande.getId(), adminId, formuleSouhaitee);
        return toModel(demande);
    }

    @Override
    public Page<DemandeAbonnementModel> mesDemandes(Long adminId, int page, int taille) {
        return demandeRepository.findByAdminId(adminId, recentes(page, taille)).map(this::toModel);
    }

    @Override
    public Page<DemandeAbonnementModel> lister(StatutDemande statut, int page, int taille) {
        PageRequest pageRequest = recentes(page, taille);
        return (statut == null ? demandeRepository.findAll(pageRequest)
                : demandeRepository.findByStatut(statut, pageRequest)).map(this::toModel);
    }

    @Override
    @Transactional
    public DemandeAbonnementModel traiter(Long superAdminId, Long demandeId, StatutDemande statut, String reponse) {
        if (statut == StatutDemande.EN_ATTENTE) {
            throw new RegleGestionException("STATUT_INVALIDE", "Une demande se traite (TRAITEE) ou se refuse (REFUSEE).");
        }
        DemandeAbonnement demande = demandeRepository.findWithDetailsById(demandeId)
                .orElseThrow(() -> new ResourceNotFoundException("DEMANDE_INTROUVABLE", "Aucune demande " + demandeId + "."));
        if (demande.getStatut() != StatutDemande.EN_ATTENTE) {
            throw new ConflitException("DEMANDE_DEJA_TRAITEE", "Cette demande a déjà reçu une réponse.");
        }
        Utilisateur superAdmin = utilisateurRepository.findById(superAdminId).orElseThrow();
        String texteReponse = reponse == null || reponse.isBlank() ? null : reponse.trim();
        demande.setStatut(statut);
        demande.setReponse(texteReponse);
        demande.setTraiteePar(superAdmin);
        demande.setDateTraitement(Instant.now());

        // Aucun changement d'abonnement (RG-09) : acceptée, l'admin paie lui-même la formule.
        String contenu = (statut == StatutDemande.TRAITEE
                ? "Demande acceptée (formule " + libelle(demande.getFormuleSouhaitee()) + ") : choisissez-la et payez-la "
                + "depuis la page Abonnement."
                : "Demande refusée.") + (texteReponse == null ? "" : "\n" + texteReponse);
        messagerie.poster(messagerie.conversation(demande.getAdmin(), null), superAdmin, CoteConversation.PLATEFORME,
                TypeMessage.REPONSE_DEMANDE, demande.getId(), contenu, false);
        notificationService.notifier(demande.getAdmin().getId(), TypeNotification.DEMANDE_ABONNEMENT_TRAITEE,
                "Réponse à votre demande", statut == StatutDemande.TRAITEE
                        ? "Batulo a accepté votre demande : vous pouvez payer la formule depuis la page Abonnement."
                        : "Batulo a refusé votre demande. Voir la messagerie.");
        log.info("Demande d'abonnement {} : {} par le super admin {}", demandeId, statut, superAdminId);
        return toModel(demande);
    }

    private DemandeAbonnementModel toModel(DemandeAbonnement d) {
        Optional<Abonnement> abonnement = abonnementRepository.findByEntrepriseId(d.getEntreprise().getId());
        Utilisateur admin = d.getAdmin();
        return new DemandeAbonnementModel(d.getId(), d.getEntreprise().getId(), d.getEntreprise().getNom(),
                admin.getId(), Messagerie.nom(admin), admin.getCourriel(), d.getFormuleSouhaitee(),
                abonnement.map(Abonnement::getFormule).orElse(null),
                abonnement.map(a -> RegleAbonnement.statutEffectif(a, Instant.now(), joursAlerte)).orElse(null),
                d.getMessage(), d.getStatut(), d.getReponse(),
                d.getTraiteePar() == null ? null : Messagerie.nom(d.getTraiteePar()),
                d.getDateDemande(), d.getDateTraitement());
    }

    private static String libelle(Formule formule) {
        return formule == Formule.ANNUEL ? "annuelle" : "mensuelle";
    }

    private static PageRequest recentes(int page, int taille) {
        return PageRequest.of(page, taille, Sort.by(Sort.Order.desc("dateDemande"), Sort.Order.desc("id")));
    }
}
