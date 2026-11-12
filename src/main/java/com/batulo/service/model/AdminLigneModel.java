package com.batulo.service.model;

import com.batulo.enums.Formule;
import com.batulo.enums.StatutAbonnement;

import java.time.Instant;

/**
 * Ligne de la liste des admins du super admin : l'entreprise, son admin, son abonnement et son nombre de
 * gestionnaires.
 *
 * @param abonnement null si l'entreprise n'a pas d'abonnement
 */
public record AdminLigneModel(
        Entreprise entreprise,
        Admin admin,
        Abonnement abonnement,
        long nombreGestionnaires
) {

    public record Entreprise(Long id, String nom, boolean actif) {
    }

    public record Admin(Long id, String prenom, String nom, String courriel, boolean actif,
                        boolean motDePasseTemporaire, Instant motDePasseTemporaireExpireLe, Instant dateCreation) {
    }

    /** Statut recalculé à partir des dates. */
    public record Abonnement(Formule formule, StatutAbonnement statut, Instant dateExpiration, Long joursRestants) {
    }
}
