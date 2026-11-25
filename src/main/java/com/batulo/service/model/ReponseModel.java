package com.batulo.service.model;

import com.batulo.enums.Langue;

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
        /** Heure du consentement (case cochée), si la version l'exigeait ; null sinon. */
        Instant dateConsentement,
        /** Langue dans laquelle le répondant a vu le formulaire. */
        Langue langue,
        /** Invité qui a répondu : seulement si la version est nominative ; null sinon (réponse anonyme). */
        InviteModel invite,
        List<ValeurReponseModel> valeurs
) {
}
