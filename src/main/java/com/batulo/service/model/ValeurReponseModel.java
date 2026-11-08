package com.batulo.service.model;

import com.batulo.enums.TypeQuestion;
import com.fasterxml.jackson.databind.JsonNode;

/** La réponse à une question, dans le même format que l'envoi. */
public record ValeurReponseModel(
        Long questionId,
        String libelle,
        TypeQuestion type,
        JsonNode valeur
) {
}
