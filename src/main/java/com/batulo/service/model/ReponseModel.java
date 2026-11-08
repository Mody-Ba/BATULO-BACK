package com.batulo.service.model;

import java.time.Instant;
import java.util.List;

/** Une réponse reçue (anonyme), avec ses valeurs dans l'ordre du formulaire. */
public record ReponseModel(
        Long id,
        Instant dateSoumission,
        List<ValeurReponseModel> valeurs
) {
}
