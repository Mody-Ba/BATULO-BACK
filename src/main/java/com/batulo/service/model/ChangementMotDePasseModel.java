package com.batulo.service.model;

public record ChangementMotDePasseModel(
        String motDePasseActuel,
        String nouveauMotDePasse,
        String confirmationMotDePasse
) {
    @Override
    public String toString() {
        return "ChangementMotDePasseModel[****]";
    }
}
