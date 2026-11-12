package com.batulo.service.model;

import com.batulo.enums.Formule;
import com.batulo.enums.StatutAbonnement;

import java.time.Instant;

/**
 * Tableau de bord de l'admin. Les sondages sont comptés une fois par lignée, sur la même ligne que la liste
 * filtrée par ?derniereVersion=true ; les réponses, toutes versions confondues.
 *
 * @param abonnement null si l'entreprise n'a pas d'abonnement
 */
public record TableauDeBordModel(
        Abonnement abonnement,
        Gestionnaires gestionnaires,
        Sondages sondages,
        long nombreTotalReponses
) {

    /** Statut recalculé à partir des dates. */
    public record Abonnement(Formule formule, StatutAbonnement statut, Instant dateExpiration, Long joursRestants) {
    }

    public record Gestionnaires(long actifs, long desactives) {
    }

    public record Sondages(long nonCommences, long enCours, long termines) {
    }
}
