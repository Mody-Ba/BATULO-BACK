package com.batulo.service;

import com.batulo.entity.Invitation;
import com.batulo.enums.StatutInvitation;

/** Statut calculé d'une invitation ; même ordre que InvitationSpecifications.condition (SQL). */
public final class RegleInvitation {

    private RegleInvitation() {
    }

    public static StatutInvitation statut(Invitation i) {
        if (i.getRevoqueeLe() != null) {
            return StatutInvitation.REVOQUEE;
        }
        if (i.getReponduLe() != null) {
            return StatutInvitation.A_REPONDU;
        }
        if (i.getOuverteLe() != null) {
            return StatutInvitation.OUVERTE;
        }
        if (i.getEchecEnvoiLe() != null) {
            return StatutInvitation.EN_ECHEC;
        }
        return i.getPremierEnvoiLe() != null ? StatutInvitation.INVITEE : StatutInvitation.A_INVITER;
    }
}
