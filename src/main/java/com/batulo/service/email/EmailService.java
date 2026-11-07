package com.batulo.service.email;

import com.batulo.enums.Formule;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Envoi des courriels de Batulo. Interface pour pouvoir changer de fournisseur (SMTP aujourd'hui) sans toucher
 * aux services métier.
 * <p>
 * Aucune méthode ne lève d'exception : un échec d'envoi est écrit dans les logs et l'opération continue.
 * Appelées dans une transaction, les méthodes n'envoient qu'après sa validation (rien n'est envoyé si elle
 * est annulée).
 */
public interface EmailService {

    /** Mot de passe temporaire d'un compte créé ou réinitialisé par le super admin ou l'admin. */
    void envoyerMotDePasseTemporaire(String courriel, String prenom, String motDePasse, Instant expireLe);

    /** Confirmation à l'admin : abonnement payé (premier paiement) ou renouvelé. */
    void envoyerConfirmationPaiement(String courriel, String prenom, Formule formule, BigDecimal montant,
                                     String devise, Instant dateExpiration, boolean renouvellement);

    /** Rappel à l'admin : l'abonnement expire dans 7 jours ou moins. */
    void envoyerRappelExpiration(String courriel, String prenom, String entreprise, Instant dateExpiration,
                                 long joursRestants);

    /** Avis à l'admin : l'abonnement est expiré, l'accès passe en lecture seule. */
    void envoyerAbonnementExpire(String courriel, String prenom, String entreprise, Instant dateExpiration);

    /**
     * Lien « mot de passe oublié ». Prêt pour l'étape 7, qui créera le jeton et appellera cette méthode.
     *
     * @param lien adresse complète de la page de réinitialisation, jeton compris
     */
    void envoyerLienReinitialisation(String courriel, String prenom, String lien, Instant expireLe);
}
