package com.batulo.service.model;

/** Bilan d'un passage de la tâche quotidienne des abonnements. */
public record ResultatTacheModel(
        int abonnementsVerifies,
        int rappelsEnvoyes,
        int abonnementsExpires
) {
}
