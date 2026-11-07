package com.batulo.service;

import com.batulo.service.model.ChangementMotDePasseModel;
import com.batulo.service.model.ConnexionModel;
import com.batulo.service.model.IdentifiantsModel;
import com.batulo.service.model.ProfilModel;
import com.batulo.service.model.ReinitialisationMotDePasseModel;
import com.batulo.service.model.UtilisateurConnecteModel;

import java.util.Optional;

/** Authentification (section 6 du cahier des charges, AD-01, AD-02, GE-01, GE-02, RG-06, RG-07). */
public interface AuthService {

    /**
     * @throws com.batulo.exception.NonAuthentifieException IDENTIFIANTS_INVALIDES (même réponse si le courriel
     *         n'existe pas) ou MOT_DE_PASSE_TEMPORAIRE_EXPIRE (plus de 24 h)
     * @throws com.batulo.exception.ActionInterditeException COMPTE_DESACTIVE (compte ou entreprise désactivé)
     */
    ConnexionModel connexion(IdentifiantsModel identifiants);

    /**
     * Échange un jeton de rafraîchissement contre une nouvelle paire de jetons (rotation : l'ancien est révoqué).
     * Réutiliser un jeton déjà révoqué révoque toutes les sessions du compte (jeton probablement volé).
     *
     * @throws com.batulo.exception.NonAuthentifieException jeton inconnu, révoqué ou expiré
     */
    ConnexionModel rafraichir(String jetonRafraichissement);

    /** Révoque le jeton de rafraîchissement s'il appartient à l'utilisateur. Sans effet sinon. */
    void deconnexion(Long utilisateurId, String jetonRafraichissement);

    /**
     * Envoie un lien de réinitialisation (jeton UUID, 1 h, usage unique) si un compte actif correspond.
     * Ne révèle jamais si le courriel existe.
     */
    void motDePasseOublie(String courriel);

    /**
     * @throws com.batulo.exception.RegleGestionException JETON_INVALIDE (inconnu, expiré ou déjà utilisé)
     *         ou MOTS_DE_PASSE_DIFFERENTS
     */
    void reinitialiserMotDePasse(ReinitialisationMotDePasseModel reinitialisation);

    /**
     * Remplace le mot de passe (temporaire ou non), révoque les autres sessions et ouvre une nouvelle session.
     *
     * @throws com.batulo.exception.RegleGestionException MOT_DE_PASSE_ACTUEL_INCORRECT, MOTS_DE_PASSE_DIFFERENTS
     *         ou MOT_DE_PASSE_IDENTIQUE
     */
    ConnexionModel changerMotDePasse(Long utilisateurId, ChangementMotDePasseModel changement);

    ProfilModel moi(Long utilisateurId);

    /** État du compte relu à chaque requête par le filtre JWT ; vide si le compte n'existe plus. */
    Optional<UtilisateurConnecteModel> utilisateurConnecte(Long utilisateurId);
}
