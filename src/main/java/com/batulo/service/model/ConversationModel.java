package com.batulo.service.model;

import com.batulo.enums.Role;
import com.batulo.enums.TypeMessage;

import java.time.Instant;

/** Une conversation vue par l'utilisateur connecté : son interlocuteur, le dernier message, ses non lus. */
public record ConversationModel(
        Long id,
        Interlocuteur interlocuteur,
        /** Null si la conversation n'a encore aucun message. */
        DernierMessage dernierMessage,
        long nonLus,
        Instant dernierMessageLe
) {
    /** Batulo (super admins) : id null, rôle SUPER_ADMIN. */
    public record Interlocuteur(Long id, String prenom, String nom, Role role, String entreprise) {
    }

    /** recu : envoyé par l'interlocuteur (et non par soi). */
    public record DernierMessage(String extrait, Instant dateEnvoi, String expediteurNom, TypeMessage type,
                                 boolean recu) {
    }
}
