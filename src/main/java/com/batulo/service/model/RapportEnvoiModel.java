package com.batulo.service.model;

import java.util.List;

/** Envoi ou relance demandés : courriels mis en file (envoyés en arrière-plan) et invitations ignorées. */
public record RapportEnvoiModel(int enFile, List<Ignoree> ignorees) {

    /**
     * @param raison INTROUVABLE, REVOQUEE, A_REPONDU, DEJA_INVITEE, ENVOI_EN_COURS, JAMAIS_INVITEE ou DELAI_RELANCE
     */
    public record Ignoree(Long invitationId, String raison) {
    }
}
