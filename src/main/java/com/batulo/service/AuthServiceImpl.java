package com.batulo.service;

import com.batulo.configs.JwtUtils;
import com.batulo.entity.Entreprise;
import com.batulo.entity.JetonRafraichissement;
import com.batulo.entity.JetonReinitialisation;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.StatutAbonnement;
import com.batulo.exception.ActionInterditeException;
import com.batulo.exception.NonAuthentifieException;
import com.batulo.exception.RegleGestionException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.AbonnementRepository;
import com.batulo.repository.JetonRafraichissementRepository;
import com.batulo.repository.JetonReinitialisationRepository;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.email.EmailService;
import com.batulo.service.mappers.UtilisateurMapper;
import com.batulo.service.model.ChangementMotDePasseModel;
import com.batulo.service.model.ConnexionModel;
import com.batulo.service.model.IdentifiantsModel;
import com.batulo.service.model.ProfilModel;
import com.batulo.service.model.ReinitialisationMotDePasseModel;
import com.batulo.service.model.UtilisateurConnecteModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private static final String MESSAGE_IDENTIFIANTS = "Courriel ou mot de passe incorrect.";

    private final UtilisateurRepository utilisateurRepository;
    private final AbonnementRepository abonnementRepository;
    private final JetonRafraichissementRepository jetonRafraichissementRepository;
    private final JetonReinitialisationRepository jetonReinitialisationRepository;
    private final UtilisateurMapper utilisateurMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final EmailService emailService;

    /**
     * Hash calculé une fois : comparé quand le courriel est inconnu, pour que la réponse prenne le même temps
     * qu'avec un vrai compte (on ne peut pas deviner les courriels inscrits au chronomètre).
     */
    private final String hashFactice;

    @Value("${batulo.jwt.duree-rafraichissement-jours}")
    private long dureeRafraichissementJours;

    @Value("${batulo.mot-de-passe.duree-lien-reinitialisation-minutes}")
    private long dureeLienMinutes;

    @Value("${batulo.mot-de-passe.url-reinitialisation}")
    private String urlReinitialisation;

    @Value("${batulo.abonnement.jours-alerte-expiration}")
    private int joursAlerte;

    public AuthServiceImpl(UtilisateurRepository utilisateurRepository,
                           AbonnementRepository abonnementRepository,
                           JetonRafraichissementRepository jetonRafraichissementRepository,
                           JetonReinitialisationRepository jetonReinitialisationRepository,
                           UtilisateurMapper utilisateurMapper,
                           PasswordEncoder passwordEncoder,
                           JwtUtils jwtUtils,
                           EmailService emailService) {
        this.utilisateurRepository = utilisateurRepository;
        this.abonnementRepository = abonnementRepository;
        this.jetonRafraichissementRepository = jetonRafraichissementRepository;
        this.jetonReinitialisationRepository = jetonReinitialisationRepository;
        this.utilisateurMapper = utilisateurMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.emailService = emailService;
        this.hashFactice = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Override
    @Transactional
    public ConnexionModel connexion(IdentifiantsModel identifiants) {
        String courriel = NormaliseurCourriel.normaliser(identifiants.courriel());
        Optional<Utilisateur> trouve = utilisateurRepository.findByCourriel(courriel);
        if (trouve.isEmpty()) {
            passwordEncoder.matches(identifiants.motDePasse(), hashFactice);
            throw new NonAuthentifieException("IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS);
        }
        Utilisateur utilisateur = trouve.get();
        if (!passwordEncoder.matches(identifiants.motDePasse(), utilisateur.getMotDePasse())) {
            throw new NonAuthentifieException("IDENTIFIANTS_INVALIDES", MESSAGE_IDENTIFIANTS);
        }
        // Après le mot de passe seulement : on ne révèle rien sur un compte à qui ne connaît pas son mot de passe.
        verifierCompteUtilisable(utilisateur);
        log.info("Connexion du compte {}", utilisateur.getId());
        return ouvrirSession(utilisateur);
    }

    @Override
    @Transactional(noRollbackFor = NonAuthentifieException.class)
    public ConnexionModel rafraichir(String jetonRafraichissement) {
        JetonRafraichissement jeton = jetonRafraichissementRepository
                .findByJetonHash(HachageJeton.hacher(jetonRafraichissement))
                .orElseThrow(AuthServiceImpl::sessionInvalide);
        Utilisateur utilisateur = jeton.getUtilisateur();
        if (jeton.isRevoque()) {
            // Un jeton révoqué qui revient a probablement été volé : toutes les sessions du compte sont fermées.
            jetonRafraichissementRepository.revoquerTous(utilisateur.getId());
            log.warn("Jeton de rafraîchissement déjà utilisé présenté pour le compte {} : sessions révoquées",
                    utilisateur.getId());
            throw sessionInvalide();
        }
        if (!jeton.getExpireLe().isAfter(Instant.now())) {
            jeton.setRevoque(true);
            throw sessionInvalide();
        }
        jeton.setRevoque(true);
        verifierCompteUtilisable(utilisateur);
        return ouvrirSession(utilisateur);
    }

    @Override
    @Transactional
    public void deconnexion(Long utilisateurId, String jetonRafraichissement) {
        jetonRafraichissementRepository.findByJetonHash(HachageJeton.hacher(jetonRafraichissement))
                .filter(jeton -> jeton.getUtilisateur().getId().equals(utilisateurId))
                .ifPresent(jeton -> jeton.setRevoque(true));
        log.info("Déconnexion du compte {}", utilisateurId);
    }

    @Override
    @Transactional
    public void motDePasseOublie(String courriel) {
        Optional<Utilisateur> trouve = utilisateurRepository.findByCourriel(NormaliseurCourriel.normaliser(courriel));
        if (trouve.isEmpty() || !estUtilisable(trouve.get())) {
            log.info("Mot de passe oublié : aucun compte actif pour ce courriel, aucun lien envoyé");
            return;
        }
        Utilisateur utilisateur = trouve.get();
        Instant maintenant = Instant.now();
        // Un seul lien valable à la fois : les précédents ne fonctionnent plus.
        jetonReinitialisationRepository.invaliderPourUtilisateur(utilisateur.getId(), maintenant);

        String jetonClair = UUID.randomUUID().toString();
        JetonReinitialisation jeton = new JetonReinitialisation();
        jeton.setUtilisateur(utilisateur);
        jeton.setJetonHash(HachageJeton.hacher(jetonClair));
        jeton.setExpireLe(maintenant.plus(Duration.ofMinutes(dureeLienMinutes)));
        jetonReinitialisationRepository.save(jeton);

        String lien = UriComponentsBuilder.fromUriString(urlReinitialisation)
                .queryParam("jeton", jetonClair).build().toUriString();
        emailService.envoyerLienReinitialisation(utilisateur.getCourriel(), utilisateur.getPrenom(), lien,
                jeton.getExpireLe());
        log.info("Mot de passe oublié : lien de réinitialisation créé pour le compte {}", utilisateur.getId());
    }

    @Override
    @Transactional
    public void reinitialiserMotDePasse(ReinitialisationMotDePasseModel reinitialisation) {
        verifierConfirmation(reinitialisation.nouveauMotDePasse(), reinitialisation.confirmationMotDePasse());
        Instant maintenant = Instant.now();
        JetonReinitialisation jeton = jetonReinitialisationRepository
                .findByJetonHash(HachageJeton.hacher(reinitialisation.jeton().trim()))
                .filter(j -> j.getUtiliseLe() == null && j.getExpireLe().isAfter(maintenant))
                .orElseThrow(() -> new RegleGestionException("JETON_INVALIDE",
                        "Ce lien est invalide, expiré ou déjà utilisé. Demandez un nouveau lien."));
        jeton.setUtiliseLe(maintenant);
        Utilisateur utilisateur = jeton.getUtilisateur();
        remplacerMotDePasse(utilisateur, reinitialisation.nouveauMotDePasse());
        log.info("Mot de passe réinitialisé par lien pour le compte {}", utilisateur.getId());
    }

    @Override
    @Transactional
    public ConnexionModel changerMotDePasse(Long utilisateurId, ChangementMotDePasseModel changement) {
        Utilisateur utilisateur = utilisateurRepository.findWithEntrepriseById(utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("UTILISATEUR_INTROUVABLE", "Compte introuvable."));
        if (!passwordEncoder.matches(changement.motDePasseActuel(), utilisateur.getMotDePasse())) {
            throw new RegleGestionException("MOT_DE_PASSE_ACTUEL_INCORRECT", "Le mot de passe actuel est incorrect.");
        }
        verifierConfirmation(changement.nouveauMotDePasse(), changement.confirmationMotDePasse());
        if (passwordEncoder.matches(changement.nouveauMotDePasse(), utilisateur.getMotDePasse())) {
            throw new RegleGestionException("MOT_DE_PASSE_IDENTIQUE",
                    "Le nouveau mot de passe doit être différent de l'actuel.");
        }
        remplacerMotDePasse(utilisateur, changement.nouveauMotDePasse());
        log.info("Mot de passe changé par le compte {}", utilisateurId);
        return ouvrirSession(utilisateur);
    }

    @Override
    public ProfilModel moi(Long utilisateurId) {
        Utilisateur utilisateur = utilisateurRepository.findWithEntrepriseById(utilisateurId)
                .orElseThrow(() -> new ResourceNotFoundException("UTILISATEUR_INTROUVABLE", "Compte introuvable."));
        return new ProfilModel(utilisateurMapper.toModel(utilisateur), statutAbonnement(utilisateur));
    }

    @Override
    public Optional<UtilisateurConnecteModel> utilisateurConnecte(Long utilisateurId) {
        return utilisateurRepository.findWithEntrepriseById(utilisateurId)
                .map(u -> new UtilisateurConnecteModel(
                        u.getId(),
                        u.getCourriel(),
                        u.getRole(),
                        u.getEntreprise() == null ? null : u.getEntreprise().getId(),
                        u.isActif(),
                        u.isMotDePasseTemporaire(),
                        statutAbonnement(u)));
    }

    /** Nouveau mot de passe définitif : fin du mot de passe temporaire et fermeture de toutes les sessions. */
    private void remplacerMotDePasse(Utilisateur utilisateur, String nouveauMotDePasse) {
        utilisateur.setMotDePasse(passwordEncoder.encode(nouveauMotDePasse));
        utilisateur.setMotDePasseTemporaire(false);
        utilisateur.setMotDePasseTemporaireExpireLe(null);
        jetonRafraichissementRepository.revoquerTous(utilisateur.getId());
    }

    private ConnexionModel ouvrirSession(Utilisateur utilisateur) {
        String jetonClair = HachageJeton.nouveauJeton();
        JetonRafraichissement jeton = new JetonRafraichissement();
        jeton.setUtilisateur(utilisateur);
        jeton.setJetonHash(HachageJeton.hacher(jetonClair));
        jeton.setExpireLe(Instant.now().plus(Duration.ofDays(dureeRafraichissementJours)));
        jetonRafraichissementRepository.save(jeton);

        return new ConnexionModel(
                jwtUtils.genererJeton(utilisateur.getId(), utilisateur.getRole()),
                jwtUtils.dureeAcces().toSeconds(),
                jetonClair,
                utilisateurMapper.toModel(utilisateur),
                statutAbonnement(utilisateur));
    }

    /** RG-01 et RG-06 : compte et entreprise actifs, mot de passe temporaire encore valable. */
    private void verifierCompteUtilisable(Utilisateur utilisateur) {
        if (!estUtilisable(utilisateur)) {
            throw new ActionInterditeException("COMPTE_DESACTIVE",
                    "Votre compte ou votre entreprise a été désactivé. Contactez votre administrateur.");
        }
        Instant expiration = utilisateur.getMotDePasseTemporaireExpireLe();
        if (utilisateur.isMotDePasseTemporaire() && expiration != null && !expiration.isAfter(Instant.now())) {
            throw new NonAuthentifieException("MOT_DE_PASSE_TEMPORAIRE_EXPIRE",
                    "Votre mot de passe temporaire a expiré (24 h). Demandez-en un nouveau à votre administrateur "
                            + "ou utilisez « mot de passe oublié ».");
        }
    }

    private static boolean estUtilisable(Utilisateur utilisateur) {
        Entreprise entreprise = utilisateur.getEntreprise();
        return utilisateur.isActif() && (entreprise == null || entreprise.isActif());
    }

    /** Null pour le super admin (pas d'entreprise). */
    private StatutAbonnement statutAbonnement(Utilisateur utilisateur) {
        if (utilisateur.getEntreprise() == null) {
            return null;
        }
        return abonnementRepository.findByEntrepriseId(utilisateur.getEntreprise().getId())
                .map(a -> RegleAbonnement.statutEffectif(a, Instant.now(), joursAlerte))
                .orElse(StatutAbonnement.EN_ATTENTE_PAIEMENT);
    }

    private static void verifierConfirmation(String motDePasse, String confirmation) {
        if (!motDePasse.equals(confirmation)) {
            throw new RegleGestionException("MOTS_DE_PASSE_DIFFERENTS",
                    "Le nouveau mot de passe et sa confirmation ne correspondent pas.");
        }
    }

    private static NonAuthentifieException sessionInvalide() {
        return new NonAuthentifieException("JETON_RAFRAICHISSEMENT_INVALIDE",
                "Session expirée ou révoquée : reconnectez-vous.");
    }
}
