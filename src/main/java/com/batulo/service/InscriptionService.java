package com.batulo.service;

import com.batulo.service.model.InscriptionEffectueeModel;
import com.batulo.service.model.InscriptionModel;

public interface InscriptionService {

    /**
     * Inscrit l'admin d'une entreprise : crée l'entreprise, le compte ADMIN (mot de passe chiffré avec BCrypt)
     * et un abonnement EN_ATTENTE_PAIEMENT. Aucun abonnement n'est actif tant que le paiement n'est pas validé.
     *
     * @throws com.batulo.exception.RegleGestionException mot de passe et confirmation différents
     * @throws com.batulo.exception.CompteExistantException un compte existe déjà avec ce courriel
     */
    InscriptionEffectueeModel inscrire(InscriptionModel inscription);
}
