package com.batulo.service.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JetonRafraichissementRequest(
        @NotBlank(message = "le jeton de rafraîchissement est obligatoire")
        @Size(max = 200, message = "jeton de rafraîchissement invalide")
        String jetonRafraichissement
) {
    @Override
    public String toString() {
        return "JetonRafraichissementRequest[jetonRafraichissement=****]";
    }
}
