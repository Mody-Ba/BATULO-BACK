package com.batulo.service.model;

import java.time.Instant;
import java.util.List;

/** Une réponse reçue (anonyme), avec ses valeurs dans l'ordre du formulaire. */
public record ReponseResponse(
        Long id,
        Instant dateSoumission,
        List<ValeurReponseResponse> valeurs
) {
}
