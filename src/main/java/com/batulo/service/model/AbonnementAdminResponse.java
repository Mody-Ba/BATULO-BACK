package com.batulo.service.model;

public record AbonnementAdminResponse(
        AbonnementResponse abonnement,
        Long adminId,
        String adminPrenom,
        String adminNom,
        String adminCourriel
) {
}
