package com.batulo.service.model;

import com.batulo.enums.OffreInscription;
/** Données d'inscription transmises au service (mot de passe encore en clair, chiffré par le service). */
public record InscriptionModel(
        String prenom,
        String nom,
        String courriel,
        String motDePasse,
        String confirmationMotDePasse,
        String nomEntreprise,
        OffreInscription offre,
        Integer nombreSondages
) {
    @Override
    public String toString() {
        return "InscriptionModel[prenom=" + prenom + ", nom=" + nom + ", courriel=" + courriel
                + ", motDePasse=****, confirmationMotDePasse=****, nomEntreprise=" + nomEntreprise + ", offre=" + offre
                + ", nombreSondages=" + nombreSondages + "]";
    }
}
