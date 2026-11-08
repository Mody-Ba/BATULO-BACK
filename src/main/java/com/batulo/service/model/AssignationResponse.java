package com.batulo.service.model;

import java.time.Instant;

/** Un gestionnaire assigné au sondage. */
public record AssignationResponse(
        Long gestionnaireId,
        String prenom,
        String nom,
        String courriel,
        /** Un gestionnaire désactivé garde ses assignations ; il les retrouve à sa réactivation. */
        boolean actif,
        Instant dateAssignation,
        /** Admin qui a fait l'assignation ; null si son compte a été supprimé. */
        AuteurResponse assignePar
) {
}
