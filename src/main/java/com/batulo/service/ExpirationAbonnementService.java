package com.batulo.service;

import com.batulo.service.model.ResultatTacheModel;

/**
 * Tâche quotidienne des abonnements (section 5 du cahier des charges) :
 * <ul>
 *   <li>7 jours ou moins avant l'échéance : rappel UNE seule fois par période payée (courriel et notification à
 *   l'admin, notification aux super admins), abonnement EXPIRE_BIENTOT ;</li>
 *   <li>date d'expiration dépassée : abonnement EXPIRE, avis unique (courriel et notification à l'admin,
 *   notification aux super admins).</li>
 * </ul>
 * Un nouveau paiement remet les deux avis à zéro. Les entreprises désactivées sont ignorées.
 */
public interface ExpirationAbonnementService {

    /** Peut être relancée sans risque : un avis déjà envoyé ne l'est jamais deux fois. */
    ResultatTacheModel verifierAbonnements();
}
