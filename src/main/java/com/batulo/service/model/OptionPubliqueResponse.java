package com.batulo.service.model;

/** Option d'une question du formulaire public ; la réponse désigne l'option par sa valeur. */
public record OptionPubliqueResponse(
        Long id,
        String libelle,
        String valeur,
        int ordre
) {
}
