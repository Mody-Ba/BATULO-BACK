package com.batulo.service.email;

import java.time.Instant;

/** Envoi des courriels aux utilisateurs Batulo. */
public interface EmailService {

    /** Communique un mot de passe temporaire (création du compte ou renvoi par le super admin). */
    void envoyerMotDePasseTemporaire(String courriel, String prenom, String motDePasse, Instant expireLe);
}
