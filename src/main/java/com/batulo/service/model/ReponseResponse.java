package com.batulo.service.model;

import java.time.Instant;
import java.util.List;

/** Une réponse reçue (anonyme), avec ses valeurs dans l'ordre du formulaire. */
public record ReponseResponse(
        Long id,
        /** Id de la version qui a reçu la réponse. */
        Long sondageId,
        /** Numéro de cette version dans la lignée. */
        int version,
        Instant dateSoumission,
        /** Heure du consentement (case cochée), si la version l'exigeait ; null sinon. */
        Instant dateConsentement,
        List<ValeurReponseResponse> valeurs
) {
}
