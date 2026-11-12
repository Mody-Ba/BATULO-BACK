package com.batulo.service.model;

import com.batulo.enums.Formule;
import com.batulo.enums.StatutAbonnement;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** GET /api/admin/tableau-de-bord. abonnement : null si l'entreprise n'en a pas. */
public record TableauDeBordResponse(
        Abonnement abonnement,
        Gestionnaires gestionnaires,
        Sondages sondages,
        long nombreTotalReponses
) {

    public record Abonnement(Formule formule, StatutAbonnement statut, Instant dateExpiration, Long joursRestants) {
    }

    public record Gestionnaires(long actifs, long desactives) {
    }

    /** Une lignée par sondage, clés égales aux valeurs de StatutSondage. */
    public record Sondages(@JsonProperty("NON_COMMENCE") long nonCommences,
                           @JsonProperty("EN_COURS") long enCours,
                           @JsonProperty("TERMINE") long termines) {
    }
}
