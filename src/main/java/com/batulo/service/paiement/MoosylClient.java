package com.batulo.service.paiement;

import com.batulo.service.model.SessionMoosylModel;

import java.util.Optional;

/**
 * Appels à l'API Moosyl (paiement mobile). Isole le reste du code du protocole HTTP de Moosyl ; remplacé par un
 * mock dans les tests. La clé secrète ne quitte jamais cette couche et n'est jamais journalisée.
 */
public interface MoosylClient {

    /**
     * Crée une session de paiement (POST /checkout-session).
     *
     * @param transactionId référence Batulo, unique par environnement Moosyl
     * @param montantMru    montant en MRU entiers
     * @param telephone     numéro à 8 chiffres du payeur ; null si inconnu (Moosyl le demande alors)
     * @throws com.batulo.exception.MoosylNonConfigureException clé absente
     * @throws com.batulo.exception.MoosylIndisponibleException refus ou panne de Moosyl
     */
    SessionMoosylModel creerSession(String transactionId, long montantMru, String telephone, String urlRetour,
                                    int expirationMinutes);

    /**
     * Statut d'une session tel que Moosyl le donne (open, completed, expired, cancelled), en minuscules.
     * Relecture de secours : vide si Moosyl ne répond pas ou si la réponse n'est pas comprise (le webhook reste la
     * source principale).
     */
    Optional<String> statutSession(String sessionId);
}
