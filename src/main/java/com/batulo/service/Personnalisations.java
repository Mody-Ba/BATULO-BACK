package com.batulo.service;

import com.batulo.entity.Entreprise;
import com.batulo.entity.EntrepriseLogo;
import com.batulo.repository.EntrepriseLogoRepository;
import com.batulo.repository.InfosLogo;
import com.batulo.service.model.ApparenceModel;
import com.batulo.service.model.LogoInfoModel;
import com.batulo.service.model.PersonnalisationModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;


/**
 * Valeurs effectives de la personnalisation d'une entreprise (nom affiché, couleur, logo), partagées par les
 * réglages, le formulaire public, l'aperçu, les rapports PDF et la fiche super admin. Le contenu du logo n'est lu
 * que pour les rapports ({@link #logoPourRapport}).
 */
@Component
public class Personnalisations {

    /** Préfixe de la route publique du logo (suivi du jeton). */
    public static final String ROUTE_LOGO = "/api/public/logos/";

    private final EntrepriseLogoRepository logoRepository;
    private final String couleurDefaut;
    private final String urlBase;

    public Personnalisations(EntrepriseLogoRepository logoRepository,
                             @Value("${batulo.personnalisation.couleur-defaut:#005CBB}") String couleurDefaut,
                             @Value("${batulo.url-base}") String urlBase) {
        if (!ContrasteCouleur.valide(couleurDefaut)) {
            throw new IllegalStateException("batulo.personnalisation.couleur-defaut doit être au format #RRGGBB");
        }
        this.logoRepository = logoRepository;
        this.couleurDefaut = ContrasteCouleur.normaliser(couleurDefaut);
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
    }

    public PersonnalisationModel de(Entreprise entreprise) {
        String couleur = couleurEffective(entreprise);
        return new PersonnalisationModel(entreprise.getNom(), entreprise.getNomAffiche(), nomEffectif(entreprise),
                entreprise.getCouleurPrincipale(), couleur, ContrasteCouleur.couleurTexte(couleur),
                logoRepository.findInfos(entreprise.getId()).map(this::toModel).orElse(null));
    }

    public ApparenceModel apparence(Entreprise entreprise) {
        String couleur = couleurEffective(entreprise);
        return new ApparenceModel(nomEffectif(entreprise),
                logoRepository.findInfos(entreprise.getId()).map(l -> url(l.jeton())).orElse(null),
                couleur, ContrasteCouleur.couleurTexte(couleur));
    }

    /** Contenu du logo (PNG ou JPEG) pour l'en-tête des rapports ; null sans logo. */
    public byte[] logoPourRapport(Entreprise entreprise) {
        return logoRepository.findById(entreprise.getId()).map(EntrepriseLogo::getContenu).orElse(null);
    }

    public String nomEffectif(Entreprise entreprise) {
        return entreprise.getNomAffiche() != null ? entreprise.getNomAffiche() : entreprise.getNom();
    }

    private String couleurEffective(Entreprise entreprise) {
        return entreprise.getCouleurPrincipale() != null ? entreprise.getCouleurPrincipale() : couleurDefaut;
    }

    private LogoInfoModel toModel(InfosLogo logo) {
        return new LogoInfoModel(url(logo.jeton()), logo.typeMime(), logo.taille(), logo.largeur(), logo.hauteur(),
                logo.dateModification());
    }

    private String url(String jeton) {
        return urlBase + ROUTE_LOGO + jeton;
    }
}
