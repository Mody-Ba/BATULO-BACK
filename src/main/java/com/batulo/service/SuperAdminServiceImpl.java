package com.batulo.service;

import com.batulo.entity.Abonnement;
import com.batulo.entity.Entreprise;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import com.batulo.enums.StatutAbonnement;
import com.batulo.exception.ActionInterditeException;
import com.batulo.exception.CompteExistantException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.AbonnementRepository;
import com.batulo.repository.AbonnementSpecifications;
import com.batulo.repository.EntrepriseRepository;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.email.EmailService;
import com.batulo.service.mappers.AbonnementMapper;
import com.batulo.service.mappers.UtilisateurMapper;
import com.batulo.service.model.AbonnementAdminModel;
import com.batulo.service.model.AbonnementModel;
import com.batulo.service.model.AdminDetailModel;
import com.batulo.service.model.CreationAdminModel;
import com.batulo.service.model.ModificationAdminModel;
import com.batulo.service.model.MotDePasseTemporaireModel;
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
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class SuperAdminServiceImpl implements SuperAdminService {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminServiceImpl.class);

    private final UtilisateurRepository utilisateurRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final AbonnementRepository abonnementRepository;
    private final UtilisateurMapper utilisateurMapper;
    private final AbonnementMapper abonnementMapper;
    private final MotDePasseTemporaire motDePasseTemporaire;
    private final EmailService emailService;

    @Value("${batulo.abonnement.jours-alerte-expiration}")
    private int joursAlerte;

    public SuperAdminServiceImpl(UtilisateurRepository utilisateurRepository,
                                 EntrepriseRepository entrepriseRepository,
                                 AbonnementRepository abonnementRepository,
                                 UtilisateurMapper utilisateurMapper,
                                 AbonnementMapper abonnementMapper,
                                 MotDePasseTemporaire motDePasseTemporaire,
                                 EmailService emailService) {
        this.utilisateurRepository = utilisateurRepository;
        this.entrepriseRepository = entrepriseRepository;
        this.abonnementRepository = abonnementRepository;
        this.utilisateurMapper = utilisateurMapper;
        this.abonnementMapper = abonnementMapper;
        this.motDePasseTemporaire = motDePasseTemporaire;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public AdminDetailModel creerAdmin(CreationAdminModel creation) {
        String courriel = NormaliseurCourriel.normaliser(creation.courriel());
        if (utilisateurRepository.existsByCourriel(courriel)) {
            throw new CompteExistantException();
        }
        String prenom = creation.prenom().trim();
        String nom = creation.nom().trim();

        Entreprise entreprise = new Entreprise();
        entreprise.setNom(creation.nomEntreprise() == null || creation.nomEntreprise().isBlank()
                ? "Entreprise de " + prenom + " " + nom
                : creation.nomEntreprise().trim());
        entreprise.setActif(true);
        entreprise = entrepriseRepository.save(entreprise);

        Utilisateur admin = new Utilisateur();
        admin.setPrenom(prenom);
        admin.setNom(nom);
        admin.setCourriel(courriel);
        admin.setRole(Role.ADMIN);
        admin.setEntreprise(entreprise);
        admin.setActif(true);
        String motDePasse = motDePasseTemporaire.attribuer(admin);
        admin = utilisateurRepository.save(admin);

        // RG-09 : pas d'abonnement manuel, l'admin devra payer en ligne pour l'activer.
        Abonnement abonnement = new Abonnement();
        abonnement.setEntreprise(entreprise);
        abonnement.setStatut(StatutAbonnement.EN_ATTENTE_PAIEMENT);
        abonnement = abonnementRepository.save(abonnement);

        emailService.envoyerMotDePasseTemporaire(courriel, prenom, motDePasse, admin.getMotDePasseTemporaireExpireLe());
        log.info("Super admin : admin {} et entreprise {} créés, abonnement {} en attente de paiement",
                admin.getId(), entreprise.getId(), abonnement.getId());
        return new AdminDetailModel(utilisateurMapper.toModel(admin), versModele(abonnement, Instant.now()), 0);
    }

    @Override
    public Page<UtilisateurModel> listerAdmins(int page, int taille) {
        return utilisateurRepository.findByRole(Role.ADMIN,
                        PageRequest.of(page, taille, Sort.by(Sort.Direction.DESC, "dateCreation", "id")))
                .map(utilisateurMapper::toModel);
    }

    @Override
    public AdminDetailModel detailAdmin(Long adminId) {
        return detail(adminTrouve(adminId));
    }

    @Override
    @Transactional
    public AdminDetailModel modifierAdmin(Long adminId, ModificationAdminModel modification) {
        Utilisateur admin = adminTrouve(adminId);
        String courriel = NormaliseurCourriel.normaliser(modification.courriel());
        if (utilisateurRepository.existsByCourrielAndIdNot(courriel, adminId)) {
            throw new CompteExistantException();
        }
        admin.setPrenom(modification.prenom().trim());
        admin.setNom(modification.nom().trim());
        admin.setCourriel(courriel);
        admin.getEntreprise().setNom(modification.nomEntreprise().trim());
        log.info("Super admin : admin {} modifié", adminId);
        return detail(admin);
    }

    @Override
    @Transactional
    public void supprimerAdmin(Long adminId) {
        Utilisateur admin = adminTrouve(adminId);
        Long entrepriseId = admin.getEntreprise().getId();
        long gestionnaires = utilisateurRepository.countByEntrepriseIdAndRole(entrepriseId, Role.GESTIONNAIRE);
        entrepriseRepository.supprimerAvecDependances(entrepriseId);
        log.info("Super admin : admin {} supprimé avec l'entreprise {}, {} gestionnaire(s), abonnement et paiements",
                adminId, entrepriseId, gestionnaires);
    }

    @Override
    @Transactional
    public AdminDetailModel changerActivation(Long adminId, boolean actif) {
        Utilisateur admin = adminTrouve(adminId);
        Entreprise entreprise = admin.getEntreprise();
        // RG-01 : l'entreprise désactivée bloque aussi tous ses gestionnaires, sans toucher à leur propre état.
        entreprise.setActif(actif);
        admin.setActif(actif);
        abonnementRepository.findByEntrepriseId(entreprise.getId()).ifPresent(abonnement ->
                abonnement.setStatut(actif
                        ? RegleAbonnement.statutEffectif(abonnement, Instant.now(), joursAlerte)
                        : StatutAbonnement.DESACTIVE));
        log.info("Super admin : admin {} et entreprise {} {}", adminId, entreprise.getId(),
                actif ? "activés" : "désactivés");
        return detail(admin);
    }

    @Override
    @Transactional
    public MotDePasseTemporaireModel renvoyerMotDePasseTemporaire(Long adminId) {
        Utilisateur admin = adminTrouve(adminId);
        String motDePasse = motDePasseTemporaire.attribuer(admin);
        emailService.envoyerMotDePasseTemporaire(admin.getCourriel(), admin.getPrenom(), motDePasse,
                admin.getMotDePasseTemporaireExpireLe());
        log.info("Super admin : nouveau mot de passe temporaire pour l'admin {}", adminId);
        return new MotDePasseTemporaireModel(admin.getId(), admin.getCourriel(), admin.getMotDePasseTemporaireExpireLe());
    }

    @Override
    public Page<AbonnementAdminModel> listerAbonnements(StatutAbonnement statut, int page, int taille) {
        Instant maintenant = Instant.now();
        // Expiration la plus proche en premier. Les jamais payés (date null) arrivent à la fin : c'est l'ordre
        // par défaut de PostgreSQL en tri croissant (nullsLast() n'est pas supporté avec les Specifications).
        Sort tri = Sort.by(Sort.Order.asc("dateExpiration"), Sort.Order.asc("id"));
        Page<Abonnement> abonnements = abonnementRepository.findAll(
                AbonnementSpecifications.parStatut(statut, maintenant, joursAlerte), PageRequest.of(page, taille, tri));

        // Les admins de la page en une seule requête.
        Map<Long, Utilisateur> admins = utilisateurRepository.findByEntrepriseIdInAndRole(
                        abonnements.map(a -> a.getEntreprise().getId()).toList(), Role.ADMIN).stream()
                .collect(Collectors.toMap(u -> u.getEntreprise().getId(), Function.identity(), (a, b) -> a));

        return abonnements.map(abonnement -> {
            Utilisateur admin = admins.get(abonnement.getEntreprise().getId());
            return new AbonnementAdminModel(versModele(abonnement, maintenant),
                    admin == null ? null : admin.getId(),
                    admin == null ? null : admin.getPrenom(),
                    admin == null ? null : admin.getNom(),
                    admin == null ? null : admin.getCourriel());
        });
    }

    /**
     * Trouve un admin. Le super admin est protégé (403) ; un gestionnaire ou un id inconnu donne 404.
     */
    private Utilisateur adminTrouve(Long adminId) {
        Utilisateur utilisateur = utilisateurRepository.findWithEntrepriseById(adminId)
                .orElseThrow(() -> adminIntrouvable(adminId));
        if (utilisateur.getRole() == Role.SUPER_ADMIN) {
            throw new ActionInterditeException("SUPER_ADMIN_PROTEGE",
                    "Le compte du super admin ne peut pas être consulté, modifié, désactivé ni supprimé ici.");
        }
        if (utilisateur.getRole() != Role.ADMIN) {
            throw adminIntrouvable(adminId);
        }
        return utilisateur;
    }

    private static ResourceNotFoundException adminIntrouvable(Long adminId) {
        return new ResourceNotFoundException("ADMIN_INTROUVABLE", "Aucun admin avec l'identifiant " + adminId + ".");
    }

    private AdminDetailModel detail(Utilisateur admin) {
        Long entrepriseId = admin.getEntreprise().getId();
        AbonnementModel abonnement = abonnementRepository.findByEntrepriseId(entrepriseId)
                .map(a -> versModele(a, Instant.now()))
                .orElse(null);
        long gestionnaires = utilisateurRepository.countByEntrepriseIdAndRole(entrepriseId, Role.GESTIONNAIRE);
        return new AdminDetailModel(utilisateurMapper.toModel(admin), abonnement, gestionnaires);
    }

    private AbonnementModel versModele(Abonnement abonnement, Instant maintenant) {
        return abonnementMapper.toModel(abonnement,
                RegleAbonnement.statutEffectif(abonnement, maintenant, joursAlerte),
                RegleAbonnement.joursRestants(abonnement, maintenant));
    }
}
