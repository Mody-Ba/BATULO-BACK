package com.batulo.service.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record SoumissionModel(
        String jetonRepondant,
        List<ValeurSoumise> reponses
) {
    public record ValeurSoumise(Long questionId, JsonNode valeur) {
    }

    /** Le jeton du répondant n'est jamais écrit dans les logs. */
    @Override
    public String toString() {
        return "SoumissionModel[jetonRepondant=****, reponses=" + reponses.size() + "]";
    }
}
