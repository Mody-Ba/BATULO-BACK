package com.batulo.service;

import com.batulo.service.model.LogoPublicModel;
import com.batulo.service.model.PersonnalisationInfoModel;
import com.batulo.service.model.PersonnalisationModel;

/** Personnalisation de l'entreprise : l'admin modifie, ses gestionnaires lisent. */
public interface PersonnalisationService {

    /** Personnalisation de l'entreprise de l'utilisateur (admin ou gestionnaire). */
    PersonnalisationModel lire(Long utilisateurId);

    /**
     * Nom affiché et couleur ; null = inchangé, texte vide = effacé.
     *
     * @throws com.batulo.exception.RegleGestionException COULEUR_INVALIDE
     */
    PersonnalisationModel modifier(Long utilisateurId, PersonnalisationInfoModel info);

    /**
     * Ajoute ou remplace le logo ; l'URL change (nouveau jeton), l'ancienne ne sert plus rien.
     *
     * @throws com.batulo.exception.RegleGestionException LOGO_FORMAT_INVALIDE, LOGO_DIMENSIONS, LOGO_ILLISIBLE
     * @throws com.batulo.exception.FichierTropGrosException au-delà de 1 Mo
     */
    PersonnalisationModel remplacerLogo(Long utilisateurId, byte[] contenu);

    PersonnalisationModel supprimerLogo(Long utilisateurId);

    /** Route publique : 404 LOGO_INTROUVABLE si le jeton ne désigne aucun logo actuel. */
    LogoPublicModel logoPublic(String jeton);
}
