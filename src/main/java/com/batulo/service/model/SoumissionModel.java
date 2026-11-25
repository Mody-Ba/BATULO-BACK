package com.batulo.service.model;

import com.batulo.enums.Langue;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record SoumissionModel(
        String jetonRepondant,
        /** Jeton du lien personnel ; null : lien public. */
        String jetonInvitation,
        /** Case de consentement cochée (null : non envoyée). */
        Boolean consentement,
        /** Langue choisie par le répondant ; null : langue principale. */
        Langue langue,
        List<ValeurSoumise> reponses
) {
    public record ValeurSoumise(Long questionId, JsonNode valeur) {
    }

    /** Le jeton du répondant n'est jamais écrit dans les logs. */
    @Override
    public String toString() {
        return "SoumissionModel[jetonRepondant=****, jetonInvitation=****, reponses=" + reponses.size() + "]";
    }
}
