package com.batulo.service.model;

import java.time.Instant;
import java.util.List;

/** Une réponse reçue (anonyme), avec ses valeurs dans l'ordre du formulaire. */
public record ReponseModel(
        Long id,
        /** Id de la version qui a reçu la réponse. */
        Long sondageId,
        /** Numéro de cette version dans la lignée. */
        int version,
        Instant dateSoumission,
        List<ValeurReponseModel> valeurs
) {
}
