package com.batulo.service;

import com.batulo.entity.Sondage;
import com.batulo.enums.StatutSondage;

import java.time.Instant;

/**
 * Statut d'un sondage, déduit de ses dates : toujours juste, sans tâche planifiée.
 * Doit rester cohérent avec SondageSpecifications (filtre de la liste).
 */
public final class RegleSondage {

    private RegleSondage() {
    }

    public static StatutSondage statut(Sondage sondage, Instant maintenant) {
        if (sondage.getDatePublication() == null) {
            return StatutSondage.NON_COMMENCE;
        }
        if (sondage.getFermeLe() != null
                || (sondage.getDateFermeture() != null && !maintenant.isBefore(sondage.getDateFermeture()))) {
            return StatutSondage.TERMINE;
        }
        if (maintenant.isBefore(sondage.getDatePublication())) {
            return StatutSondage.NON_COMMENCE;
        }
        return StatutSondage.EN_COURS;
    }
}
