package com.batulo.service.model;

public record PaiementConfirmeResponse(
        String message,
        AbonnementResponse abonnement
) {
}
