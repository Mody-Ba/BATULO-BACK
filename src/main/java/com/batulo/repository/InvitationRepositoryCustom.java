package com.batulo.repository;

import com.batulo.enums.StatutInvitation;

import java.util.Map;

public interface InvitationRepositoryCustom {

    /** Nombre d'invitations de la lignée par statut (une agrégation sum(case ...)), tous les statuts présents. */
    Map<StatutInvitation, Long> compterParStatut(Long ligneeId);
}
