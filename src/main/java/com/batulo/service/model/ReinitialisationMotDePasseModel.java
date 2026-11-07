package com.batulo.service.model;

public record ReinitialisationMotDePasseModel(
        String jeton,
        String nouveauMotDePasse,
        String confirmationMotDePasse
) {
    @Override
    public String toString() {
        return "ReinitialisationMotDePasseModel[****]";
    }
}
