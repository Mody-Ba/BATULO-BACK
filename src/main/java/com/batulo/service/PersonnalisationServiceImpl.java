package com.batulo.service;

import com.batulo.entity.Entreprise;
import com.batulo.entity.EntrepriseLogo;
import com.batulo.entity.Utilisateur;
import com.batulo.enums.Role;
import com.batulo.exception.ActionInterditeException;
import com.batulo.exception.RegleGestionException;
import com.batulo.exception.ResourceNotFoundException;
import com.batulo.repository.EntrepriseLogoRepository;
import com.batulo.service.model.LogoPublicModel;
import com.batulo.service.model.PersonnalisationInfoModel;
import com.batulo.service.model.PersonnalisationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class PersonnalisationServiceImpl implements PersonnalisationService {

    private static final Logger log = LoggerFactory.getLogger(PersonnalisationServiceImpl.class);
    private static final int NOM_AFFICHE_MAX = 150;

    private final AccesSondage accesSondage;
    private final EntrepriseLogoRepository logoRepository;
    private final ValidateurLogo validateurLogo;
    private final Personnalisations personnalisations;

    public PersonnalisationServiceImpl(AccesSondage accesSondage, EntrepriseLogoRepository logoRepository,
                                       ValidateurLogo validateurLogo, Personnalisations personnalisations) {
        this.accesSondage = accesSondage;
        this.logoRepository = logoRepository;
        this.validateurLogo = validateurLogo;
        this.personnalisations = personnalisations;
    }

    @Override
    public PersonnalisationModel lire(Long utilisateurId) {
        return personnalisations.de(accesSondage.acteur(utilisateurId).getEntreprise());
    }

    @Override
    @Transactional
    public PersonnalisationModel modifier(Long utilisateurId, PersonnalisationInfoModel info) {
        Entreprise entreprise = entrepriseDeLAdmin(utilisateurId);
        if (info.couleurPrincipale() != null) {
            String couleur = info.couleurPrincipale().trim();
            if (couleur.isEmpty()) {
                entreprise.setCouleurPrincipale(null);
            } else if (ContrasteCouleur.valide(couleur)) {
                entreprise.setCouleurPrincipale(ContrasteCouleur.normaliser(couleur));
            } else {
                throw new RegleGestionException("COULEUR_INVALIDE",
                        "La couleur doit être au format hexadécimal #RRGGBB (ex. #1E6B52).");
            }
        }
        if (info.nomAffiche() != null) {
            String nom = info.nomAffiche().trim();
            entreprise.setNomAffiche(nom.isEmpty() ? null : nom.substring(0, Math.min(nom.length(), NOM_AFFICHE_MAX)));
        }
        log.info("Entreprise {} : apparence modifiée par le compte {}", entreprise.getId(), utilisateurId);
        return personnalisations.de(entreprise);
    }

    @Override
    @Transactional
    public PersonnalisationModel remplacerLogo(Long utilisateurId, byte[] contenu) {
        Entreprise entreprise = entrepriseDeLAdmin(utilisateurId);
        ValidateurLogo.LogoValide valide = validateurLogo.valider(contenu);
        EntrepriseLogo logo = logoRepository.findById(entreprise.getId()).orElseGet(() -> {
            EntrepriseLogo nouveau = new EntrepriseLogo();
            nouveau.setEntrepriseId(entreprise.getId());
            return nouveau;
        });
        // Nouveau jeton à chaque remplacement : l'URL est versionnée, les caches gardent l'ancienne sans risque.
        logo.setJeton(UUID.randomUUID().toString());
        logo.setTypeMime(valide.typeMime());
        logo.setTaille(contenu.length);
        logo.setLargeur(valide.largeur());
        logo.setHauteur(valide.hauteur());
        logo.setContenu(contenu);
        logo.setDateModification(Instant.now());
        logoRepository.saveAndFlush(logo);
        log.info("Entreprise {} : logo remplacé ({}, {} octets, {}x{}) par le compte {}", entreprise.getId(),
                valide.typeMime(), contenu.length, valide.largeur(), valide.hauteur(), utilisateurId);
        return personnalisations.de(entreprise);
    }

    @Override
    @Transactional
    public PersonnalisationModel supprimerLogo(Long utilisateurId) {
        Entreprise entreprise = entrepriseDeLAdmin(utilisateurId);
        logoRepository.findById(entreprise.getId()).ifPresent(logo -> {
            logoRepository.delete(logo);
            logoRepository.flush();
            log.info("Entreprise {} : logo supprimé par le compte {}", entreprise.getId(), utilisateurId);
        });
        return personnalisations.de(entreprise);
    }

    @Override
    public LogoPublicModel logoPublic(String jeton) {
        return logoRepository.findByJeton(jeton)
                .map(l -> new LogoPublicModel(l.getContenu(), l.getTypeMime()))
                .orElseThrow(() -> new ResourceNotFoundException("LOGO_INTROUVABLE", "Ce logo n'existe pas."));
    }

    /** Seul l'admin modifie l'apparence (la sécurité de /api/admin/** le garantit déjà ; double contrôle). */
    private Entreprise entrepriseDeLAdmin(Long utilisateurId) {
        Utilisateur acteur = accesSondage.acteur(utilisateurId);
        if (acteur.getRole() != Role.ADMIN) {
            throw new ActionInterditeException("ACTION_RESERVEE_ADMIN",
                    "Seul l'admin de l'entreprise peut modifier son apparence.");
        }
        return acteur.getEntreprise();
    }
}
