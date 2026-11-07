package com.batulo.service.model;

public record IdentifiantsModel(
        String courriel,
        String motDePasse
) {
    @Override
    public String toString() {
        return "IdentifiantsModel[courriel=" + courriel + ", motDePasse=****]";
    }
}
