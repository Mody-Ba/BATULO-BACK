package com.batulo.service;

import com.batulo.entity.Abonnement;
import com.batulo.entity.Entreprise;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import com.batulo.enums.StatutAbonnement;
import com.batulo.exception.CompteExistantException;
import com.batulo.exception.RegleGestionException;
import com.batulo.repository.AbonnementRepository;
import com.batulo.repository.EntrepriseRepository;
import com.batulo.repository.UtilisateurRepository;
import com.batulo.service.mappers.UtilisateurMapper;
import com.batulo.service.model.InscriptionEffectueeModel;
import com.batulo.service.model.InscriptionModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InscriptionServiceImpl implements InscriptionService {

    private static final Logger log = LoggerFactory.getLogger(InscriptionServiceImpl.class);

    private final UtilisateurRepository utilisateurRepository;
    private final EntrepriseRepository entrepriseRepository;
    private final AbonnementRepository abonnementRepository;
    private final UtilisateurMapper utilisateurMapper;
    private final PasswordEncoder passwordEncoder;

    public InscriptionServiceImpl(UtilisateurRepository utilisateurRepository,
                                  EntrepriseRepository entrepriseRepository,
                                  AbonnementRepository abonnementRepository,
                                  UtilisateurMapper utilisateurMapper,
                                  PasswordEncoder passwordEncoder) {
        this.utilisateurRepository = utilisateurRepository;
        this.entrepriseRepository = entrepriseRepository;
        this.abonnementRepository = abonnementRepository;
        this.utilisateurMapper = utilisateurMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public InscriptionEffectueeModel inscrire(InscriptionModel inscription) {
        if (!inscription.motDePasse().equals(inscription.confirmationMotDePasse())) {
            throw new RegleGestionException("MOTS_DE_PASSE_DIFFERENTS",
                    "Le mot de passe et sa confirmation ne correspondent pas.");
        }
        String courriel = NormaliseurCourriel.normaliser(inscription.courriel());
        if (utilisateurRepository.existsByCourriel(courriel)) {
            throw new CompteExistantException();
        }
        String prenom = inscription.prenom().trim();
        String nom = inscription.nom().trim();

        Entreprise entreprise = new Entreprise();
        entreprise.setNom(inscription.nomEntreprise() == null || inscription.nomEntreprise().isBlank()
                ? "Entreprise de " + prenom + " " + nom
                : inscription.nomEntreprise().trim());
        entreprise.setActif(true);
        entreprise = entrepriseRepository.save(entreprise);

        Utilisateur admin = new Utilisateur();
        admin.setPrenom(prenom);
        admin.setNom(nom);
        admin.setCourriel(courriel);
        admin.setMotDePasse(passwordEncoder.encode(inscription.motDePasse()));
        admin.setRole(Role.ADMIN);
        admin.setEntreprise(entreprise);
        admin.setActif(true);
        admin = utilisateurRepository.save(admin);

        // RG-08 / RG-09 : l'abonnement n'existe qu'en attente ; seul un paiement en ligne l'activera.
        Abonnement abonnement = new Abonnement();
        abonnement.setEntreprise(entreprise);
        abonnement.setStatut(StatutAbonnement.EN_ATTENTE_PAIEMENT);
        abonnement = abonnementRepository.save(abonnement);

        log.info("Inscription : admin {} et entreprise {} créés, abonnement {} en attente de paiement",
                admin.getId(), entreprise.getId(), abonnement.getId());
        return new InscriptionEffectueeModel(utilisateurMapper.toModel(admin), abonnement.getId(), abonnement.getStatut());
    }
}
