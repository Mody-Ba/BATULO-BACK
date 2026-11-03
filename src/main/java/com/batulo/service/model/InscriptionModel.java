package com.batulo.service.model;

/** Données d'inscription transmises au service (mot de passe encore en clair, chiffré par le service). */
public record InscriptionModel(
        String prenom,
        String nom,
        String courriel,
        String motDePasse,
        String confirmationMotDePasse,
        String nomEntreprise
) {
    @Override
    public String toString() {
        return "InscriptionModel[prenom=" + prenom + ", nom=" + nom + ", courriel=" + courriel
                + ", motDePasse=****, confirmationMotDePasse=****, nomEntreprise=" + nomEntreprise + "]";
    }
}
